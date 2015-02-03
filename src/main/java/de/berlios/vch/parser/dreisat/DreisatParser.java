package de.berlios.vch.parser.dreisat;

import java.net.URI;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Invalidate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.apache.felix.ipojo.annotations.Validate;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.osgi.service.log.LogService;

import com.sun.syndication.feed.module.mediarss.MediaEntryModule;
import com.sun.syndication.feed.module.mediarss.io.MediaModuleParser;
import com.sun.syndication.feed.module.mediarss.types.MediaContent;
import com.sun.syndication.feed.synd.SyndEnclosure;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;

import de.berlios.vch.http.client.HttpUtils;
import de.berlios.vch.parser.AsxParser;
import de.berlios.vch.parser.HtmlParserUtils;
import de.berlios.vch.parser.IOverviewPage;
import de.berlios.vch.parser.IWebPage;
import de.berlios.vch.parser.IWebParser;
import de.berlios.vch.parser.OverviewPage;
import de.berlios.vch.parser.VideoPage;
import de.berlios.vch.parser.WebPageTitleComparator;

@Component
@Provides
public class DreisatParser implements IWebParser {

    public static final String BASE_URL = "http://www.3sat.de";

    private static final String LANDING_PAGE = BASE_URL + "/page/?source=/specials/133576/index.html";

    public static final String CHARSET = "UTF-8";

    public static final String ID = DreisatParser.class.getName();

    public static Map<String, String> HTTP_HEADERS = new HashMap<String, String>();
    static {
        HTTP_HEADERS.put("User-Agent", "Mozilla/5.0 (X11; Linux i686; rv:10.0.3) Gecko/20100101 Firefox/10.0.3");
        HTTP_HEADERS.put("Accept-Language", "de-de,de;q=0.8,en-us;q=0.5,en;q=0.3");
    }

    public DreisatFeedParser feedParser;

    @Requires
    private LogService logger;

    @Override
    public IOverviewPage getRoot() throws Exception {
        OverviewPage page = new OverviewPage();
        page.setParser(ID);
        page.setTitle(getTitle());
        page.setUri(new URI("vchpage://localhost/" + getId()));

        // add all rss feeds to
        String landingPage = HttpUtils.get(LANDING_PAGE, HTTP_HEADERS, CHARSET);
        Elements tableRows = HtmlParserUtils.getTags(landingPage, "table.article_table_main_fix tr");
        Iterator<Element> iter = tableRows.iterator();
        while (iter.hasNext()) {
            Element tr = iter.next();
            if ("tr".equalsIgnoreCase(tr.nodeName())) {
                if (tr.childNodeSize() < 10) {
                    continue;
                }

                Element td = (Element) tr.childNode(1);
                String title = td.text();

                td = (Element) tr.childNode(7);
                if ("a".equalsIgnoreCase(td.childNode(0).nodeName())) {
                    Element rss = (Element) td.childNode(0);
                    String path = rss.attr("href");
                    if (path.startsWith("/mediaplayer/rss/")) {
                        OverviewPage feedPage = new OverviewPage();
                        feedPage.setParser(ID);
                        feedPage.setTitle(title);
                        feedPage.setUri(new URI(BASE_URL + path));
                        page.getPages().add(feedPage);
                    }
                } else {
                    logger.log(LogService.LOG_DEBUG, title + " has no RSS feed");
                }
            }
        }

        // add the general 3sat mediathek feed
        OverviewPage feedPage = new OverviewPage();
        feedPage.setParser(ID);
        feedPage.setTitle("3sat-Mediathek allgemein");
        feedPage.setUri(new URI(BASE_URL + "/mediathek/rss/mediathek.xml"));
        page.getPages().add(feedPage);
        Collections.sort(page.getPages(), new WebPageTitleComparator());
        return page;
    }

    @Override
    public String getTitle() {
        return "3sat Mediathek";
    }

    @Override
    public IWebPage parse(IWebPage page) throws Exception {
        if (page instanceof VideoPage) {
            VideoPage vpage = (VideoPage) page;
            if (vpage.getVideoUri().toString().endsWith(".asx")) {
                String uri = AsxParser.getUri(vpage.getVideoUri().toString());
                vpage.setVideoUri(new URI(uri));
                page.getUserData().remove("video");
            }

            String content = HttpUtils.get(vpage.getUri().toString(), null, CHARSET);
            Element img = HtmlParserUtils.getTag(content, "div.seite div.media img.still");
            if (img != null) {
                vpage.setThumbnail(new URI(BASE_URL + img.attr("src")));
            }

            return page;
        } else {
            SyndFeed feed = feedParser.parse(page);
            OverviewPage feedPage = new OverviewPage();
            feedPage.setParser(ID);
            feedPage.setTitle(page.getTitle());
            feedPage.setUri(page.getUri());

            for (Iterator<?> iterator = feed.getEntries().iterator(); iterator.hasNext();) {
                SyndEntry entry = (SyndEntry) iterator.next();

                if (entry.getEnclosures().size() == 0) {
                    continue;
                }

                // get the best quality enclosure
                String videoUri = getBestVideo(entry);
                if (videoUri == null) {
                    continue;
                }

                VideoPage video = new VideoPage();
                video.setParser(ID);
                video.setVideoUri(new URI(videoUri));
                video.setTitle(entry.getTitle());
                if (entry.getDescription() != null) {
                    video.setDescription(entry.getDescription().getValue());
                }
                video.setUri(new URI(entry.getLink()));
                Calendar pubCal = Calendar.getInstance();
                pubCal.setTime(entry.getPublishedDate());
                video.setPublishDate(pubCal);

                // check the foreign markup for additional info
                @SuppressWarnings("unchecked")
                List<org.jdom.Element> fm = (List<org.jdom.Element>) entry.getForeignMarkup();
                for (org.jdom.Element element : fm) {
                    // look, if we have a duration in the foreign markup
                    if ("duration".equals(element.getName())) {
                        try {
                            video.setDuration(Long.parseLong(element.getText()));
                        } catch (Exception e) {
                        }
                    }

                    // look, if we have a thumbnail in the foreign markup
                    if ("thumbnail".equals(element.getName())) {
                        try {
                            video.setThumbnail(new URI(element.getText()));
                        } catch (Exception e) {
                        }
                    }
                }

                feedPage.getPages().add(video);
            }
            return feedPage;
        }
    }

    @SuppressWarnings("unchecked")
    private String getBestVideo(SyndEntry entry) {
        // first check for yahoo media rss contents, because they have better metadata
        // to pick the best video stream
        MediaContent[] contents = getMediaContents(entry);
        if (contents != null) {
            Arrays.sort(contents, new MediaContentComparator());
            return contents[contents.length - 1].getReference().toString();
        } else {
            // yahoo media rss not available, use syndenclosures
            Collections.sort(entry.getEnclosures(), new SyndEnclosureComparator());
            Collections.reverse(entry.getEnclosures());
            return ((SyndEnclosure) entry.getEnclosures().get(0)).getUrl();
        }
    }

    private MediaContent[] getMediaContents(SyndEntry entry) {
        @SuppressWarnings("unchecked")
        List<org.jdom.Element> fms = (List<org.jdom.Element>) entry.getForeignMarkup();

        MediaEntryModule module = null;
        for (org.jdom.Element element : fms) {
            if (MediaEntryModule.URI.equals(element.getNamespaceURI())) {
                MediaModuleParser parser = new MediaModuleParser();
                module = (MediaEntryModule) parser.parse(element);
                break;
            }
        }

        if (module == null) {
            module = (MediaEntryModule) entry.getModule(MediaEntryModule.URI);
            if (module == null) {
                return null;
            }
        }

        MediaContent[] contents = module.getMediaContents();
        if (contents.length == 0) {
            if (module.getMediaGroups().length > 0) {
                contents = module.getMediaGroups()[0].getContents();
            }
        }
        return contents;
    }

    @Validate
    public void start() {
        feedParser = new DreisatFeedParser(logger);
    }

    @Invalidate
    public void stop() {
    }

    @Override
    public String getId() {
        return ID;
    }
}