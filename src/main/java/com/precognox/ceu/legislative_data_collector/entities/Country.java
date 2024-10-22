package com.precognox.ceu.legislative_data_collector.entities;

import com.precognox.ceu.legislative_data_collector.ScrapingController;
import com.precognox.ceu.legislative_data_collector.australia.AustraliaController;
import com.precognox.ceu.legislative_data_collector.brazil_new.BrazilController;
import com.precognox.ceu.legislative_data_collector.bulgaria.BulgariaController;
import com.precognox.ceu.legislative_data_collector.chile.ChileController;
import com.precognox.ceu.legislative_data_collector.colombia.ColombiaController;
import com.precognox.ceu.legislative_data_collector.georgia.GeDataCollectorV2;
import com.precognox.ceu.legislative_data_collector.hungary.HungaryController;
import com.precognox.ceu.legislative_data_collector.india.IndiaController;
import com.precognox.ceu.legislative_data_collector.jordan.JoDataCollector;
import com.precognox.ceu.legislative_data_collector.poland.PolandController;
import com.precognox.ceu.legislative_data_collector.russia.RussiaController;
import com.precognox.ceu.legislative_data_collector.south_africa.SaController;
import com.precognox.ceu.legislative_data_collector.sweden.SwedenController;
import com.precognox.ceu.legislative_data_collector.uk.UkDataCollector;
import com.precognox.ceu.legislative_data_collector.usa.UsaController;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

@Getter
public enum Country {

    AUSTRALIA("AU", AustraliaController.class),
    BULGARIA("BG", BulgariaController.class),
    BRAZIL("BR", BrazilController.class),
    CHILE("CH", ChileController.class),
    COLOMBIA("CO", ColombiaController.class),
    GEORGIA("GE", GeDataCollectorV2.class),
    HUNGARY("HU", HungaryController.class),
    INDIA("IN", IndiaController.class),
    JORDAN("JO", JoDataCollector.class),
    POLAND("PL", PolandController.class),
    RUSSIA("RU", RussiaController.class),
    SOUTH_AFRICA("SA", SaController.class),
    SWEDEN("SW", SwedenController.class),
    UK("UK", UkDataCollector.class),
    USA("USA", UsaController.class);

    private final String prefix;
    private final Class<? extends ScrapingController> controllerClass;

    Country(String prefix, Class<? extends ScrapingController> controllerClass) {
        this.prefix = prefix;
        this.controllerClass = controllerClass;
    }

    //workaround to avoid renaming all enums
    public static @Nullable Country fromCode(String code) {
        return Arrays.stream(Country.values())
                .filter(c -> code.equals(c.prefix))
                .findAny()
                .orElse(null); //null is checked from the calling code
    }
}
