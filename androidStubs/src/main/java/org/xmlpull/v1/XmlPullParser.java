package org.xmlpull.v1;

import java.io.IOException;

/**
 * JVM stand-in for org.xmlpull.v1.XmlPullParser.
 *
 * XmlPull ships inside android.jar; the JDK has StAX instead, which is the same
 * pull model with different names. So this is the API and
 * {@link android.content.res.StaxXmlResourceParser} is a real implementation
 * over StAX — the app parses its own `res/xml/locales_config.xml` through here
 * to build the language picker, and a parser that returned END_DOCUMENT
 * immediately would leave the desktop with an empty language list and no error.
 */
public interface XmlPullParser {
    int START_DOCUMENT = 0;
    int END_DOCUMENT = 1;
    int START_TAG = 2;
    int END_TAG = 3;
    int TEXT = 4;

    int getEventType() throws XmlPullParserException;

    int next() throws XmlPullParserException, IOException;

    int nextTag() throws XmlPullParserException, IOException;

    String nextText() throws XmlPullParserException, IOException;

    String getName();

    String getNamespace();

    String getText();

    int getDepth();

    int getAttributeCount();

    String getAttributeName(int index);

    String getAttributeValue(int index);

    String getAttributeValue(String namespace, String name);

    void require(int type, String namespace, String name) throws XmlPullParserException, IOException;
}
