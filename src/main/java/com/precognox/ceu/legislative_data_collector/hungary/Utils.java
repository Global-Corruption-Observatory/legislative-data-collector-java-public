package com.precognox.ceu.legislative_data_collector.hungary;

public class Utils {

    /**
     * Removes session info and auth token from the URL, used for duplication filtering of downloaded pages.
     *
     * @return
     */
    public static String cleanUrl(String url) {
        return url.replaceAll("&p_auth=\\w+", "").replaceAll("_PairProxy_INSTANCE_[a-zA-Z0-9]+", "");
    }

}
