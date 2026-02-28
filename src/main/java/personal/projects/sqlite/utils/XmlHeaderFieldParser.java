package personal.projects.sqlite.utils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import personal.projects.sqlite.entities.HeaderField;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class XmlHeaderFieldParser {
    public static Map<String,HeaderField<?>> parseHeaderFields(String resourcePath) throws Exception {
        InputStream is = XmlHeaderFieldParser.class.getClassLoader().getResourceAsStream(resourcePath);
        if (is == null) throw new IllegalArgumentException("Header field definition not found");

        Map<String,HeaderField<?>> fields = new HashMap<>();
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.parse(is);

        NodeList nodeList = doc.getElementsByTagName("field");
        for (int i = 0; i < nodeList.getLength(); i++) {
            Element e = (Element) nodeList.item(i);
            String name = e.getAttribute("name");
            int offset = Integer.parseInt(e.getAttribute("offset"));
            int size = Integer.parseInt(e.getAttribute("size"));
            switch (size) {
                case 1->fields.put(name,new HeaderField<Byte>(name, offset, size));
                case 2->fields.put(name,new HeaderField<Short>(name, offset, size));
                case 4->fields.put(name,new HeaderField<Integer>(name, offset, size));
            }
        }
        return fields;
    }

}
