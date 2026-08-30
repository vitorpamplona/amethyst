package android.content.res;

import java.io.IOException;
import java.io.InputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.xmlpull.v1.XmlPullParserException;

/**
 * A real XmlPullParser over the JDK's StAX.
 *
 * XmlPull and StAX are the same pull model with different names, so the
 * translation is event-code mapping plus one difference in habits: XmlPull's
 * {@code next()} skips comments, processing instructions and whitespace-only
 * text, where StAX reports them. Skipping them here is what lets the app's
 * {@code while (eventType != END_DOCUMENT)} loop over
 * `locales_config.xml` behave the same on both platforms.
 *
 * External entities and DTDs are disabled: these files come from the app's own
 * resources, but a parser that resolves entities is an XXE waiting for the day
 * one does not.
 */
public final class StaxXmlResourceParser implements XmlResourceParser {
    private final XMLStreamReader reader;
    private final InputStream source;
    private int eventType = START_DOCUMENT;
    private int depth;

    public StaxXmlResourceParser(InputStream source) throws XmlPullParserException {
        this.source = source;
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
            this.reader = factory.createXMLStreamReader(source);
        } catch (XMLStreamException e) {
            throw new XmlPullParserException("could not open the XML resource: " + e.getMessage(), this, e);
        }
    }

    @Override public int getEventType() { return eventType; }

    @Override
    public int next() throws XmlPullParserException, IOException {
        try {
            while (reader.hasNext()) {
                int stax = reader.next();
                switch (stax) {
                    case XMLStreamConstants.START_ELEMENT:
                        depth++;
                        return eventType = START_TAG;
                    case XMLStreamConstants.END_ELEMENT:
                        depth--;
                        return eventType = END_TAG;
                    case XMLStreamConstants.CHARACTERS:
                    case XMLStreamConstants.CDATA:
                        // XmlPull does not stop on whitespace between tags.
                        if (reader.isWhiteSpace()) continue;
                        return eventType = TEXT;
                    case XMLStreamConstants.END_DOCUMENT:
                        return eventType = END_DOCUMENT;
                    default:
                        // Comments, PIs, DTDs, entity refs: skipped, as XmlPull does.
                        continue;
                }
            }
            return eventType = END_DOCUMENT;
        } catch (XMLStreamException e) {
            throw new XmlPullParserException("malformed XML resource: " + e.getMessage(), this, e);
        }
    }

    @Override
    public int nextTag() throws XmlPullParserException, IOException {
        int event = next();
        while (event == TEXT) event = next();
        if (event != START_TAG && event != END_TAG) {
            throw new XmlPullParserException("expected a start or end tag, found event " + event);
        }
        return event;
    }

    @Override
    public String nextText() throws XmlPullParserException, IOException {
        if (eventType != START_TAG) throw new XmlPullParserException("nextText() must be called on a start tag");
        int event = next();
        if (event == TEXT) {
            String text = getText();
            next();
            return text;
        }
        return "";
    }

    @Override
    public String getName() {
        return eventType == START_TAG || eventType == END_TAG ? reader.getLocalName() : null;
    }

    @Override
    public String getNamespace() {
        return eventType == START_TAG || eventType == END_TAG ? reader.getNamespaceURI() : null;
    }

    @Override public String getText() { return eventType == TEXT ? reader.getText() : null; }

    @Override public int getDepth() { return depth; }

    @Override public int getAttributeCount() { return eventType == START_TAG ? reader.getAttributeCount() : -1; }

    @Override
    public String getAttributeName(int index) {
        return eventType == START_TAG ? reader.getAttributeLocalName(index) : null;
    }

    @Override
    public String getAttributeValue(int index) {
        return eventType == START_TAG ? reader.getAttributeValue(index) : null;
    }

    @Override
    public String getAttributeValue(String namespace, String name) {
        if (eventType != START_TAG) return null;
        // A null namespace means "any", which is how the app reads
        // android:-prefixed attributes without knowing the URI.
        if (namespace == null) {
            for (int i = 0; i < reader.getAttributeCount(); i++) {
                if (reader.getAttributeLocalName(i).equals(name)) return reader.getAttributeValue(i);
            }
            return null;
        }
        return reader.getAttributeValue(namespace, name);
    }

    @Override
    public void require(int type, String namespace, String name) throws XmlPullParserException {
        boolean nameMatches = name == null || name.equals(getName());
        boolean namespaceMatches = namespace == null || namespace.equals(getNamespace());
        if (type != eventType || !namespaceMatches || !nameMatches) {
            throw new XmlPullParserException(
                    "expected event " + type + " <" + name + ">, found event " + eventType + " <" + getName() + ">");
        }
    }

    @Override
    public void close() {
        try {
            reader.close();
        } catch (XMLStreamException ignored) {
            // Closing is best effort; the stream below is what holds the handle.
        }
        try {
            source.close();
        } catch (IOException ignored) {
            // Same.
        }
    }
}
