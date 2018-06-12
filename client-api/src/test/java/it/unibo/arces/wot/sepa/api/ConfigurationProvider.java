package it.unibo.arces.wot.sepa.api;

import it.unibo.arces.wot.sepa.commons.exceptions.SEPAPropertiesException;
import it.unibo.arces.wot.sepa.pattern.JSAP;

import java.net.URL;

public class ConfigurationProvider {

    public static JSAP GetTestEnvConfiguration() throws SEPAPropertiesException {
        JSAP result;
        final String configuaration = System.getProperty("testConfiguration");
        if( configuaration != null){
            result = new JSAP(configuaration);
        }else{
            URL config = Thread.currentThread().getContextClassLoader().getResource("dev.jsap");
            result = new JSAP(config.getPath());
        }
        return result;
    }
}
