package org.oscim.backend;

import java.io.IOException;
import java.io.InputStream;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class XMLReaderAdapter {
	public void parse(DefaultHandler handler, InputStream is) throws IOException, SAXException  {

			MyXMLReader xmlReader = new MyXMLReader();
			xmlReader.setContentHandler(handler);
			xmlReader.parse(is);
	}
}
