package android.content.res;

import org.xmlpull.v1.XmlPullParser;

/**
 * JVM stand-in for android.content.res.XmlResourceParser — what
 * {@link Resources#getXml(int)} hands back. Same shape as Android's: a
 * {@link XmlPullParser} that also has to be closed.
 */
public interface XmlResourceParser extends XmlPullParser, AutoCloseable {
    @Override
    void close();
}
