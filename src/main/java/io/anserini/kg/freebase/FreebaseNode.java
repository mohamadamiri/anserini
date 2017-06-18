package io.anserini.kg.freebase;

import org.openrdf.rio.ntriples.NTriplesUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * An object representing a node in the Freebase knowledge graph. Each node is uniquely identified
 * by its URI and can represent a topic, a compound value type (CVT), or other metadata such as a
 * type.
 */
public class FreebaseNode {
  private final String uri;
  private final Map<String, List<String>> predicateValues = new TreeMap<>();

  public enum RdfObjectType {
    URI, STRING, TEXT, OTHER
  }

  /**
   * Constructor.
   * @param uri URI of node
   */
  public FreebaseNode(String uri) {
    this.uri = uri;
  }

  /**
   * Adds a predicate and a value to this node.
   * @param p predicate value
   * @param o object value
   * @return the node itself
   */
  public FreebaseNode addPredicateValue(String p, String o) {
    List<String> values = predicateValues.get(p);

    if (values == null) {
      values = new ArrayList<>();
      predicateValues.put(p, values);
    }

    values.add(o);
    return this;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    predicateValues.forEach((predicate, values) -> {
      for (String value : values) {
        sb.append(uri).append("\t").append(predicate).append("\t")
            .append(value).append("\t").append(".\n");
      }
    });
    return sb.toString();
  }

  public String uri() {
    return uri;
  }

  public Map<String, List<String>> getPredicateValues() {
    return predicateValues;
  }

  public static String cleanUri(String uri) {
    if (uri.charAt(0) == '<') {
      return uri.substring(1, uri.length() - 1).toLowerCase();
    } else {
      return uri;
    }
  }

  public static String normalizeObjectValue(String objectValue) {
    FreebaseNode.RdfObjectType type = FreebaseNode.getObjectType(objectValue);
    if (type.equals(FreebaseNode.RdfObjectType.URI)) {
      return FreebaseNode.cleanUri(objectValue);
    } else if (type.equals(FreebaseNode.RdfObjectType.STRING)) {
      // If the object is a string, remove enclosing quote.
      if (objectValue.contains("$")) {
        // See comment below about MQL key escaping
        return removeEnclosingQuote(undoMqlKeyEscape(objectValue));
      } else {
        return removeEnclosingQuote(objectValue);
      }
    } else if (type.equals(FreebaseNode.RdfObjectType.TEXT)) {
      return NTriplesUtil.unescapeString(objectValue);
    } else {
      return objectValue;
    }
  }

  private static String removeEnclosingQuote(String s) {
    if (s.charAt(0) == '"')
      return s.substring(1, s.length() - 1);
    else
      return s;
  }

  // As an example, for "Barack Obama", one of the facts is:
  //   http://rdf.freebase.com/key/wikipedia.en: "Barack_Hussein_Obama$002C_Jr$002E"
  //
  // The $xxxx encoding is something called MQL key escape.
  //
  // Live version of page no longer exists, but see:
  //  http://web.archive.org/web/20160726102723/http://wiki.freebase.com/wiki/MQL_key_escaping
  //
  // Fortunately, I found a snippet of code on the web for handling this:
  //  https://github.com/hackreduce/Hackathon/blob/master/src/main/java/org/hackreduce/models/FreebaseQuadRecord.java
  private static String undoMqlKeyEscape(String s) {
    String[] part = s.split("\\$");
    StringBuffer sb = new StringBuffer(part[0]);
    for (int i = 1; i<part.length; i++) {
      try {
        int code = Integer.parseInt(part[i].substring(0, 4), 16);
        sb.appendCodePoint(code).append(part[i].substring(4));
      } catch (IndexOutOfBoundsException e) {
        sb.append(part[i]);
      } catch (NumberFormatException e) {
        sb.append(part[i]);
      }
    }
    return sb.toString();
  }

  public static RdfObjectType getObjectType(String objectValue) {
    // Determine the type of this N-Triples 'value'.
    switch (objectValue.charAt(0)) {
      case '<':
        // e.g., <http://rdf.freebase.com/ns/m.02mjmr>
        return RdfObjectType.URI;
      case '"':
        if (objectValue.charAt(objectValue.length() - 1) == '"') {
          // e.g., "Hanna Bieluszko"
          return RdfObjectType.STRING;
        } else {
          // e.g., "Hanna Bieluszko"@en";
          return RdfObjectType.TEXT;
        }
      default:
        return RdfObjectType.OTHER;
    }
  }
}