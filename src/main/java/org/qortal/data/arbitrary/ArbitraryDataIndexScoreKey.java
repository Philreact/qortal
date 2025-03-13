package org.qortal.data.arbitrary;

import org.qortal.arbitrary.misc.Service;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.util.Objects;

@XmlAccessorType(XmlAccessType.FIELD)
public class ArbitraryDataIndexScoreKey {

    public String name;
    public Service service;
    public String link;

    public ArbitraryDataIndexScoreKey() {}

    public ArbitraryDataIndexScoreKey(String name, Service service, String link) {
        this.name = name;
        this.service = service;
        this.link = link;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArbitraryDataIndexScoreKey that = (ArbitraryDataIndexScoreKey) o;
        return Objects.equals(name, that.name) && service == that.service && Objects.equals(link, that.link);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, service, link);
    }

    @Override
    public String toString() {
        return "ArbitraryDataIndexScoreKey{" +
                "name='" + name + '\'' +
                ", service=" + service +
                ", link='" + link + '\'' +
                '}';
    }
}
