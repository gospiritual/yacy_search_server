/**
 *  InstanceMirror
 *  Copyright 2013 by Michael Peter Christen
 *  First released 18.02.2013 at http://yacy.net
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

package net.yacy.cora.federate.solr.instance;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import net.yacy.cora.federate.solr.connector.CachedSolrConnector;
import net.yacy.cora.federate.solr.connector.EmbeddedSolrConnector;
import net.yacy.cora.federate.solr.connector.MirrorSolrConnector;
import net.yacy.cora.federate.solr.connector.RemoteSolrConnector;
import net.yacy.cora.federate.solr.connector.SolrConnector;

public class InstanceMirror {

    private EmbeddedInstance solr0;
    private ShardInstance solr1;
    private CachedSolrConnector defaultConnector;
    private Map<String, CachedSolrConnector> connectorCache;

    public InstanceMirror() {
        this.solr0 = null;
        this.solr1 = null;
        this.defaultConnector = null;
        this.connectorCache = new HashMap<String, CachedSolrConnector>();
    }
    
    public boolean isConnected0() {
        return this.solr0 != null;
    }

    public void connect0(EmbeddedInstance c) {
        for (SolrConnector connector: connectorCache.values()) connector.close();
        this.connectorCache.clear();
        this.defaultConnector = null;
        this.solr0 = c;
    }

    public EmbeddedInstance getSolr0() {
        return this.solr0;
    }

    public void disconnect0() {
        if (this.solr0 == null) return;
        for (SolrConnector connector: connectorCache.values()) connector.close();
        this.connectorCache.clear();
        this.defaultConnector = null;
        this.solr0.close();
        this.solr0 = null;
    }

    public boolean isConnected1() {
        return this.solr1 != null;
    }

    public void connect1(ShardInstance c) {
        for (SolrConnector connector: connectorCache.values()) connector.close();
        this.connectorCache.clear();
        this.defaultConnector = null;
        this.solr1 = c;
    }

    public ShardInstance getSolr1() {
        return this.solr1;
    }

    public void disconnect1() {
        if (this.solr1 == null) return;
        for (SolrConnector connector: connectorCache.values()) connector.close();
        this.connectorCache.clear();
        this.defaultConnector = null;
        this.solr1.close();
        this.solr1 = null;
    }

    public synchronized void close() {
        this.disconnect0();
        this.disconnect1();
    }

    public String getDefaultCoreName() {
        if (this.solr0 != null) return this.solr0.getDefaultCoreName();
        if (this.solr1 != null) return this.solr1.getDefaultCoreName();
        return null;
    }

    public Collection<String> getCoreNames() {
        if (this.solr0 != null) return this.solr0.getCoreNames();
        if (this.solr1 != null) return this.solr1.getCoreNames();
        return null;
    }
    
    public SolrConnector getDefaultConnector() {
        if (this.defaultConnector != null) return this.defaultConnector;
        String defaultCoreName = this.getDefaultCoreName();
        if (defaultCoreName == null) return null;
        EmbeddedSolrConnector esc = this.solr0 == null ? null : new EmbeddedSolrConnector(this.solr0, defaultCoreName);
        RemoteSolrConnector rsc = this.solr1 == null ? null : new RemoteSolrConnector(this.solr1, defaultCoreName);
        this.defaultConnector = new CachedSolrConnector(new MirrorSolrConnector(esc, rsc), 10000, 1000, 100);
        this.connectorCache.put(defaultCoreName, this.defaultConnector);
        return this.defaultConnector;
    }

    public SolrConnector getConnector(String corename) {
        CachedSolrConnector msc = this.connectorCache.get(corename);
        if (msc != null) return msc;
        EmbeddedSolrConnector esc = this.solr0 == null ? null : new EmbeddedSolrConnector(this.solr0, corename);
        RemoteSolrConnector rsc = this.solr1 == null ? null : new RemoteSolrConnector(this.solr1, corename);
        msc = new CachedSolrConnector(new MirrorSolrConnector(esc, rsc), 10000, 1000, 100);
        this.connectorCache.put(corename, msc);
        return msc;
    }
    
    public void clearCache() {
        for (CachedSolrConnector csc: this.connectorCache.values()) csc.clearCache();
    }
    
}
