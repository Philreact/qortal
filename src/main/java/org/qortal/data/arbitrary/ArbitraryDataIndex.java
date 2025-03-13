package org.qortal.data.arbitrary;

import org.qortal.arbitrary.misc.Service;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class ArbitraryDataIndex {

    public String term;
    public String name;
    public Service service;
    public String identifier;
    public String link;

    public ArbitraryDataIndex() {}

    public ArbitraryDataIndex(String term, String name, Service service, String identifier, String link) {
        this.term = term;
        this.name = name;
        this.service = service;
        this.identifier = identifier;
        this.link = link;
    }

    @Override
    public String toString() {
        return "ArbitraryDataIndex{" +
                "term='" + term + '\'' +
                ", name='" + name + '\'' +
                ", service=" + service +
                ", identifier='" + identifier + '\'' +
                ", link='" + link + '\'' +
                '}';
    }
}
