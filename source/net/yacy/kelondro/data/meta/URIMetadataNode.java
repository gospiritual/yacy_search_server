/**
 *  URIMetadataNode
 *  Copyright 2012 by Michael Peter Christen
 *  First released 10.8.2012 at http://yacy.net
 *
 *  This file is part of YaCy Content Integration
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.kelondro.data.meta;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.date.MicroDate;
import net.yacy.cora.document.ASCII;
import net.yacy.cora.document.UTF8;
import net.yacy.cora.federate.solr.SolrType;
import net.yacy.cora.lod.vocabulary.Tagging;
import net.yacy.cora.order.Base64Order;
import net.yacy.crawler.retrieval.Response;
import net.yacy.document.Condenser;
import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.Bitfield;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.utils.crypt;

import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;


/**
 * This is the URIMetadata object implementation for Solr documents.
 * The purpose of this object is the migration from the old metadata structure to solr document.
 * Future implementations should try to replace URIMetadata objects completely by SolrDocument objects
 */
public class URIMetadataNode {

    public static CollectionSchema[] fieldList = new CollectionSchema[]{
        CollectionSchema.audiolinkscount_i, CollectionSchema.author, CollectionSchema.collection_sxt, CollectionSchema.content_type,
        CollectionSchema.coordinate_p, CollectionSchema.description, CollectionSchema.fresh_date_dt, CollectionSchema.host_id_s, CollectionSchema.id,
        CollectionSchema.imagescount_i, CollectionSchema.inboundlinks_protocol_sxt, CollectionSchema.inboundlinks_urlstub_txt,
        CollectionSchema.inboundlinkscount_i, CollectionSchema.keywords, CollectionSchema.language_s, CollectionSchema.last_modified, CollectionSchema.load_date_dt,
        CollectionSchema.md5_s, CollectionSchema.outboundlinks_protocol_sxt, CollectionSchema.outboundlinks_urlstub_txt,
        CollectionSchema.outboundlinkscount_i, CollectionSchema.publisher_t, CollectionSchema.referrer_id_txt, CollectionSchema.size_i, CollectionSchema.sku,
        CollectionSchema.text_t, CollectionSchema.title, CollectionSchema.title_words_val, CollectionSchema.url_chars_i,
        CollectionSchema.videolinkscount_i, CollectionSchema.videolinkscount_i, CollectionSchema.wordcount_i};
    
    private byte[] hash = null;
    private String urlRaw = null, keywords = null;
    private DigestURI url = null;
    private Bitfield flags = null;
    private int imagec = -1, audioc = -1, videoc = -1, appc = -1;
    private double lat = Double.NaN, lon = Double.NaN;
    private long ranking = -1; // during generation of a search result this value is set
    private SolrDocument doc = null;
    private String snippet = null;
    private WordReference word = null; // this is only used if the url is transported via remote search requests

    public URIMetadataNode(final SolrInputDocument doc) {
        this(ClientUtils.toSolrDocument(doc));
    }
    
    public URIMetadataNode(final SolrDocument doc) {
        this.doc = doc;
        this.snippet = "";
        this.word = null;
        this.ranking = Long.MIN_VALUE;
        this.hash = ASCII.getBytes(getString(CollectionSchema.id));
        this.urlRaw = getString(CollectionSchema.sku);
        try {
            this.url = new DigestURI(this.urlRaw, this.hash);
        } catch (MalformedURLException e) {
            Log.logException(e);
            this.url = null;
        }
    }

    public URIMetadataNode(final SolrInputDocument doc, final WordReference searchedWord, final long ranking) {
        this(ClientUtils.toSolrDocument(doc));
        this.word = searchedWord;
        this.ranking = ranking;
    }

    public URIMetadataNode(final SolrDocument doc, final WordReference searchedWord, final long ranking) {
        this(doc);
        this.word = searchedWord;
        this.ranking = ranking;
    }

    public SolrDocument getDocument() {
        return this.doc;
    }

    public byte[] hash() {
        return this.hash;
    }

    public String hosthash() {
        String hosthash = (String) this.doc.getFieldValue(CollectionSchema.host_id_s.getSolrFieldName());
        if (hosthash == null) hosthash = ASCII.String(this.hash, 6, 6);
        return hosthash;
    }

    public Date moddate() {
        return getDate(CollectionSchema.last_modified);
    }

    public DigestURI url() {
        return this.url;
    }

    public boolean matches(Pattern matcher) {
        return matcher.matcher(this.urlRaw.toLowerCase()).matches();
    }

    public String dc_title() {
        ArrayList<String> a = getStringList(CollectionSchema.title);
        if (a == null || a.size() == 0) return "";
        return a.get(0);
    }

    public String dc_creator() {
        return getString(CollectionSchema.author);
    }

    public String dc_publisher() {
        return getString(CollectionSchema.publisher_t);
    }

    public String dc_subject() {
        if (this.keywords == null) {
            this.keywords = getString(CollectionSchema.keywords);
        }
        return this.keywords;
    }

    public double lat() {
        if (this.lat == Double.NaN) {
            this.lon = 0.0d;
            this.lat = 0.0d;
            String latlon = (String) this.doc.getFieldValue(CollectionSchema.coordinate_p.getSolrFieldName());
            if (latlon != null) {
                int p = latlon.indexOf(',');
                if (p > 0) {
                    this.lat = Double.parseDouble(latlon.substring(0, p));
                    this.lon = Double.parseDouble(latlon.substring(p + 1));
                }
            }
        }
        return this.lat;
    }

    public double lon() {
        if (this.lon == Double.NaN) lat();
        return this.lon;
    }

    public long ranking() {
        return this.ranking;
    }

    public Date loaddate() {
        return getDate(CollectionSchema.load_date_dt);
    }

    public Date freshdate() {
        return getDate(CollectionSchema.fresh_date_dt);
    }

    public String md5() {
        return getString(CollectionSchema.md5_s);
    }

    public char doctype() {
        ArrayList<String> a = getStringList(CollectionSchema.content_type);
        if (a == null || a.size() == 0) return Response.docType(url());
        return Response.docType(a.get(0));
    }

    public byte[] language() {
        String language = getString(CollectionSchema.language_s);
        if (language == null || language.length() == 0) return ASCII.getBytes("en");
        return UTF8.getBytes(language);
    }

    public byte[] referrerHash() {
        ArrayList<String>  referrer = getStringList(CollectionSchema.referrer_id_txt);
        if (referrer == null || referrer.size() == 0) return null;
        return ASCII.getBytes(referrer.get(0));
    }

    public int size() {
        return getInt(CollectionSchema.size_i);
    }

    public Bitfield flags() {
        if (flags == null) {
            this.flags = new Bitfield();
            if (dc_subject() != null && dc_subject().indexOf("indexof") >= 0) this.flags.set(Condenser.flag_cat_indexof, true);
            if (lon() != 0.0d || lat() != 0.0d) this.flags.set(Condenser.flag_cat_haslocation, true);
            if (limage() > 0) this.flags.set(Condenser.flag_cat_hasimage, true);
            if (laudio() > 0) this.flags.set(Condenser.flag_cat_hasaudio, true);
            if (lvideo() > 0) this.flags.set(Condenser.flag_cat_hasvideo, true);
            if (lapp() > 0) this.flags.set(Condenser.flag_cat_hasapp, true);
        }
        return this.flags;
    }

    public int wordCount() {
        return getInt(CollectionSchema.wordcount_i);
    }

    public int llocal() {
        return getInt(CollectionSchema.inboundlinkscount_i);
    }

    public int lother() {
        return getInt(CollectionSchema.outboundlinkscount_i);
    }

    public int limage() {
        if (this.imagec == -1) {
            this.imagec = getInt(CollectionSchema.imagescount_i);
        }
        return this.imagec;
    }

    public int laudio() {
        if (this.audioc == -1) {
            this.audioc = getInt(CollectionSchema.audiolinkscount_i);
        }
        return this.audioc;
    }

    public int lvideo() {
        if (this.videoc == -1) {
            this.videoc = getInt(CollectionSchema.videolinkscount_i);
        }
        return this.videoc;
    }

    public int lapp() {
        if (this.appc == -1) {
            this.appc = getInt(CollectionSchema.videolinkscount_i);
        }
        return this.appc;
    }

    public int virtualAge() {
        return MicroDate.microDateDays(moddate());
    }

    public int wordsintitle() {
        ArrayList<Integer>  x = getIntList(CollectionSchema.title_words_val);
        if (x == null || x.size() == 0) return 0;
        return x.get(0).intValue();
    }

    public int urllength() {
        return getInt(CollectionSchema.url_chars_i);
    }

    public String snippet() {
        return this.snippet;
    }

    public String[] collections() {
        ArrayList<String> a = getStringList(CollectionSchema.collection_sxt);
        return a.toArray(new String[a.size()]);
    }

    public WordReference word() {
        return this.word;
    }
    
    private static List<String> indexedList2protocolList(Collection<Object> iplist, int dimension) {
        List<String> a = new ArrayList<String>(dimension);
        for (int i = 0; i < dimension; i++) a.add("http");
        if (iplist == null) return a;
        for (Object ip: iplist) a.set(Integer.parseInt(((String) ip).substring(0, 3)), ((String) ip).substring(4));
        return a;
    }

    public static Iterator<String> getLinks(SolrDocument doc, boolean inbound) {
        Collection<Object> urlstub = doc.getFieldValues((inbound ? CollectionSchema.inboundlinks_urlstub_txt :  CollectionSchema.outboundlinks_urlstub_txt).getSolrFieldName());
        Collection<String> urlprot = urlstub == null ? null : indexedList2protocolList(doc.getFieldValues((inbound ? CollectionSchema.inboundlinks_protocol_sxt : CollectionSchema.outboundlinks_protocol_sxt).getSolrFieldName()), urlstub.size());
        String u;
        LinkedHashSet<String> list = new LinkedHashSet<String>();
        if (urlprot != null && urlstub != null) {
            assert urlprot.size() == urlstub.size();
            Object[] urlprota = urlprot.toArray();
            Object[] urlstuba = urlstub.toArray();
            for (int i = 0; i < urlprota.length; i++) {
                u = ((String) urlprota[i]) + "://" + ((String) urlstuba[i]);
                int hp = u.indexOf('#');
                if (hp > 0) u = u.substring(0, hp);
                list.add(u);
            }
        }
        return list.iterator();
    }
    
    public static Date getDate(SolrDocument doc, final CollectionSchema key) {
        Date x = doc == null ? null : (Date) doc.getFieldValue(key.getSolrFieldName());
        Date now = new Date();
        return (x == null) ? new Date(0) : x.after(now) ? now : x;
    }

    public String getText() {
        return getString(CollectionSchema.text_t);
    }

    public String getDescription() {
        return getString(CollectionSchema.description);
    }    

    public boolean isOlder(URIMetadataRow other) {
        if (other == null) return false;
        final Date tmoddate = moddate();
        final Date omoddate = other.moddate();
        if (tmoddate.before(omoddate)) return true;
        if (tmoddate.equals(omoddate)) {
            final Date tloaddate = loaddate();
            final Date oloaddate = other.loaddate();
            if (tloaddate.before(oloaddate)) return true;
            if (tloaddate.equals(oloaddate)) return true;
        }
        return false;
    }

    private static StringBuilder corePropList(URIMetadataNode md) {
        // generate a parseable string; this is a simple property-list
        final StringBuilder s = new StringBuilder(300);

        // create new formatters to make concurrency possible
        final GenericFormatter formatter = new GenericFormatter(GenericFormatter.FORMAT_SHORT_DAY, GenericFormatter.time_minute);

        try {
            s.append("hash=").append(ASCII.String(md.hash()));
            s.append(",url=").append(crypt.simpleEncode(md.url().toNormalform(true)));
            s.append(",descr=").append(crypt.simpleEncode(md.dc_title()));
            s.append(",author=").append(crypt.simpleEncode(md.dc_creator()));
            s.append(",tags=").append(crypt.simpleEncode(Tagging.cleanTagFromAutotagging(md.dc_subject())));
            s.append(",publisher=").append(crypt.simpleEncode(md.dc_publisher()));
            s.append(",lat=").append(md.lat());
            s.append(",lon=").append(md.lon());
            s.append(",mod=").append(formatter.format(md.moddate()));
            s.append(",load=").append(formatter.format(md.loaddate()));
            s.append(",fresh=").append(formatter.format(md.freshdate()));
            s.append(",referrer=").append(md.referrerHash() == null ? "" : ASCII.String(md.referrerHash()));
            s.append(",md5=").append(md.md5());
            s.append(",size=").append(md.size());
            s.append(",wc=").append(md.wordCount());
            s.append(",dt=").append(md.doctype());
            s.append(",flags=").append(md.flags().exportB64());
            s.append(",lang=").append(md.language() == null ? "EN" : UTF8.String(md.language()));
            s.append(",llocal=").append(md.llocal());
            s.append(",lother=").append(md.lother());
            s.append(",limage=").append(md.limage());
            s.append(",laudio=").append(md.laudio());
            s.append(",lvideo=").append(md.lvideo());
            s.append(",lapp=").append(md.lapp());
            if (md.word() != null) {
                // append also word properties
                final String wprop = md.word().toPropertyForm();
                s.append(",wi=").append(Base64Order.enhancedCoder.encodeString(wprop));
            }
            return s;
        } catch (final Throwable e) {
            Log.logException(e);
            return null;
        }
    }

    /**
     * the toString format must be completely identical to URIMetadataRow because that is used
     * to transport the data over p2p connections.
     */
    public String toString(String snippet) {
        // add information needed for remote transport
        final StringBuilder core = corePropList(this);
        if (core == null)
            return null;

        core.ensureCapacity(core.length() + snippet.length() * 2);
        core.insert(0, '{');
        core.append(",snippet=").append(crypt.simpleEncode(snippet));
        core.append('}');

        return core.toString();
        //return "{" + core + ",snippet=" + crypt.simpleEncode(snippet) + "}";
    }


    /**
     * @return the object as String.<br>
     * This e.g. looks like this:
     * <pre>{hash=jmqfMk7Y3NKw,referrer=------------,mod=20050610,load=20051003,size=51666,wc=1392,cc=0,local=true,q=AEn,dt=h,lang=uk,url=b|aHR0cDovL3d3dy50cmFuc3BhcmVuY3kub3JnL3N1cnZleXMv,descr=b|S25vd2xlZGdlIENlbnRyZTogQ29ycnVwdGlvbiBTdXJ2ZXlzIGFuZCBJbmRpY2Vz}</pre>
     */
    @Override
    public String toString() {
        final StringBuilder core = corePropList(this);
        if (core == null) return null;
        core.insert(0, '{');
        core.append('}');
        return core.toString();
    }
    
    private DigestURI getURL(CollectionSchema field) {
        assert !field.isMultiValued();
        assert field.getType() == SolrType.string || field.getType() == SolrType.text_general || field.getType() == SolrType.text_en_splitting_tight;
        Object x = this.doc.getFieldValue(field.getSolrFieldName());
        if (x == null) return null;
        try {
            return new DigestURI((String) x);
        } catch (MalformedURLException e) {
            return null;
        }
    }

    private int getInt(CollectionSchema field) {
        assert !field.isMultiValued();
        assert field.getType() == SolrType.num_integer;
        Object x = this.doc.getFieldValue(field.getSolrFieldName());
        if (x == null) return 0;
        if (x instanceof Integer) return ((Integer) x).intValue();
        if (x instanceof Long) return ((Long) x).intValue();
        return 0;
    }

    private Date getDate(CollectionSchema field) {
        assert !field.isMultiValued();
        assert field.getType() == SolrType.date;
        Date x = (Date) this.doc.getFieldValue(field.getSolrFieldName());
        if (x == null) return new Date(0);
        Date now = new Date();
        return x.after(now) ? now : x;
    }

    private String getString(CollectionSchema field) {
        assert !field.isMultiValued();
        assert field.getType() == SolrType.string || field.getType() == SolrType.text_general || field.getType() == SolrType.text_en_splitting_tight;
        Object x = this.doc.getFieldValue(field.getSolrFieldName());
        if (x == null) return "";
        if (x instanceof ArrayList) {
            @SuppressWarnings("unchecked")
            ArrayList<String> xa = (ArrayList<String>) x;
            return xa.size() == 0 ? "" : xa.get(0);
        }
        return (String) x;
    }

    @SuppressWarnings("unchecked")
    private ArrayList<String> getStringList(CollectionSchema field) {
        assert field.isMultiValued();
        assert field.getType() == SolrType.string || field.getType() == SolrType.text_general;
        Object r = this.doc.getFieldValue(field.getSolrFieldName());
        if (r == null) return new ArrayList<String>(0);
        if (r instanceof ArrayList) {
            return (ArrayList<String>) r;
        }
        ArrayList<String> a = new ArrayList<String>(1);
        a.add((String) r);
        return a;
    }
    
    @SuppressWarnings("unchecked")
    private ArrayList<Integer> getIntList(CollectionSchema field) {
        assert field.isMultiValued();
        assert field.getType() == SolrType.num_integer;
        Object r = this.doc.getFieldValue(field.getSolrFieldName());
        if (r == null) return new ArrayList<Integer>(0);
        if (r instanceof ArrayList) {
            return (ArrayList<Integer>) r;
        }
        ArrayList<Integer> a = new ArrayList<Integer>(1);
        a.add((Integer) r);
        return a;
    }

}