package de.berlios.vch.parser.dreisat;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import com.sun.syndication.feed.module.mediarss.types.MediaContent;

public class MediaContentComparator implements Comparator<MediaContent> {

    private Map<String, Integer> typePriorities = new HashMap<String, Integer>();

    public MediaContentComparator() {
        //@formatter:off
        typePriorities.put("video/vnd.rn-realvideo", 0);
        typePriorities.put("video/3gpp",             1);
        typePriorities.put("video/quicktime",        2);
        typePriorities.put("video/x-ms-asf",         3);
        typePriorities.put("video/webm",             4);
        //@formatter:on
    }

    public int getTypePriority(String type) {
        Integer prio = typePriorities.get(type);
        return prio == null ? 0 : prio;
    }

    @Override
    public int compare(MediaContent mc1, MediaContent mc2) {
        String type1 = mc1.getType().toLowerCase();
        String type2 = mc2.getType().toLowerCase();

        if (getTypePriority(type1) > getTypePriority(type2)) {
            return 1;
        } else if (getTypePriority(type1) < getTypePriority(type2)) {
            return -1;
        } else if (mc1.getWidth() > mc2.getWidth()) {
            return 1;
        } else if (mc1.getWidth() < mc2.getWidth()) {
            return -1;
        } else if (mc1.getBitrate() > mc2.getBitrate()) {
            return 1;
        } else if (mc1.getBitrate() < mc2.getBitrate()) {
            return -1;
        }

        return 0;
    }
}
