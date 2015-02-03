package de.berlios.vch.parser.dreisat;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import com.sun.syndication.feed.synd.SyndEnclosure;

/**
 * Compares two SyndEnclosures according to their type WMV -> quicktime quicktime -> real HQ -> LQ
 * 
 * @author Henrik Niehaus
 */
public class SyndEnclosureComparator implements Comparator<SyndEnclosure> {

    private Map<String, Integer> typePriorities = new HashMap<String, Integer>();

    public SyndEnclosureComparator() {
        //@formatter:off
        typePriorities.put("video/vnd.rn-realvideo", 0);
        typePriorities.put("video/3gpp",             1);
        typePriorities.put("video/quicktime",        2);
        typePriorities.put("video/x-ms-asf",         3);
        typePriorities.put("video/webm",             4);
        //@formatter:on
    }

    @Override
    public int compare(SyndEnclosure enc1, SyndEnclosure enc2) {
        String type1 = enc1.getType().toLowerCase();
        String type2 = enc2.getType().toLowerCase();

        if (getTypePriority(type1) > getTypePriority(type2)) {
            return 1;
        } else if (getTypePriority(type1) < getTypePriority(type2)) {
            return -1;
        }

        return 0;
    }

    public int getTypePriority(String type) {
        Integer prio = typePriorities.get(type);
        return prio == null ? 0 : prio;
    }
}