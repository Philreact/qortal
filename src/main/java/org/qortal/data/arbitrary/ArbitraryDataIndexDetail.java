package org.qortal.data.arbitrary;

import org.qortal.arbitrary.misc.Service;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class ArbitraryDataIndexDetail {

    public String issuer;
    public int rank;
    public String term;
    public String name;
    public Service service;
    public String identifier;
    public String link;

    public ArbitraryDataIndexDetail() {}

    public ArbitraryDataIndexDetail(String issuer, int rank, ArbitraryDataIndex index) {
        this.issuer = issuer;
        this.rank = rank;
        this.term = index.term;
        this.name = index.name;
        this.service = index.service;
        this.identifier = index.identifier;
        this.link = index.link;
    }

    @Override
    public String toString() {
        return "ArbitraryDataIndexDetail{" +
                "issuer='" + issuer + '\'' +
                ", rank=" + rank +
                ", term='" + term + '\'' +
                ", name='" + name + '\'' +
                ", service=" + service +
                ", identifier='" + identifier + '\'' +
                ", link='" + link + '\'' +
                '}';
    }
}
