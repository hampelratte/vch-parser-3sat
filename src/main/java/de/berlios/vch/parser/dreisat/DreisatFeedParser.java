package de.berlios.vch.parser.dreisat;

import java.io.IOException;
import java.net.MalformedURLException;

import org.osgi.service.log.LogService;

import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.FeedException;

import de.berlios.vch.http.client.HttpUtils;
import de.berlios.vch.parser.IWebPage;
import de.berlios.vch.rss.RssParser;

public class DreisatFeedParser {
    private transient LogService logger;

    public DreisatFeedParser(LogService logger) {
        this.logger = logger;
    }

    public SyndFeed parse(IWebPage page) throws IllegalArgumentException, MalformedURLException, FeedException, IOException {
        String feedUri = page.getUri().toString();

        logger.log(LogService.LOG_INFO, "Parsing rss feed " + feedUri);
        String rss = HttpUtils.get(feedUri, DreisatParser.HTTP_HEADERS, "UTF-8");
        SyndFeed feed = RssParser.parse(rss);
        feed.setLink(feedUri);
        feed.setTitle(page.getTitle());

        // for (Iterator<?> iterator = feed.getEntries().iterator(); iterator.hasNext();) {
        // SyndEntry entry = (SyndEntry) iterator.next();
        // // sort enclosures, so that the best quality is enclosure[0],
        // Collections.sort(entry.getEnclosures(), new SyndEnclosureComparator());
        // Collections.reverse(entry.getEnclosures());
        // }

        return feed;
    }
}
