package com.precognox.ceu.legislative_data_collector.sweden;

import com.precognox.ceu.legislative_data_collector.entities.Originator;
import org.jsoup.nodes.Element;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class OriginatorsParser {

    /**
     * Common method to parse the originators from a bill/amendment page.
     * @param page The parsed jsoup page
     * @return Originator entities
     */
    public List<Originator> parseOriginators(Element page) {
        Stream<Element> originatorLinks =
                Optional.ofNullable(page.selectFirst("h3:contains(Intressenter)"))
                        .map(header -> header.parents().get(3))
                        .map(originatorsDiv -> originatorsDiv.getElementsByTag("a"))
                        .stream()
                        .flatMap(Collection::stream);

        return originatorLinks
                .map(link -> link.getElementsByTag("span"))
                .map(spans -> new Originator(spans.get(0).text(), spans.get(1).text()))
                .toList();
    }
}
