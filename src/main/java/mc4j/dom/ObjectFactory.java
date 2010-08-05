//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, vhudson-jaxb-ri-2.1-833 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2010.08.04 at 08:18:39 PM EDT 
//


package mc4j.dom;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the mc4j.dom package. 
 * <p>An ObjectFactory allows you to programatically 
 * construct new instances of the Java representation 
 * for XML content. The Java representation of XML 
 * content can consist of schema derived interfaces 
 * and classes representing the binding of schema 
 * type definitions, element declarations and model 
 * groups.  Factory methods for each of these are 
 * provided in this class.
 * 
 */
@XmlRegistry
public class ObjectFactory {

    private final static QName _MCAPI_QNAME = new QName("mc", "MCAPI");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: mc4j.dom
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link CodeType }
     * 
     */
    public CodeType createCodeType() {
        return new CodeType();
    }

    /**
     * Create an instance of {@link ErrorType }
     * 
     */
    public ErrorType createErrorType() {
        return new ErrorType();
    }

    /**
     * Create an instance of {@link MCAPI }
     * 
     */
    public MCAPI createMCAPI() {
        return new MCAPI();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link MCAPI }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "mc", name = "MCAPI")
    public JAXBElement<MCAPI> createMCAPI(MCAPI value) {
        return new JAXBElement<MCAPI>(_MCAPI_QNAME, MCAPI.class, null, value);
    }

}