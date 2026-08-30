package org.xmlpull.v1;

/** JVM stand-in for org.xmlpull.v1.XmlPullParserException. */
public class XmlPullParserException extends Exception {
    public XmlPullParserException(String message) { super(message); }

    public XmlPullParserException(String message, XmlPullParser parser, Throwable cause) {
        super(message, cause);
    }
}
