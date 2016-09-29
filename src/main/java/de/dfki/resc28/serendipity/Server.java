/*
 * This file is part of serendipity. It is subject to the license terms in
 * the LICENSE file found in the top-level directory of this distribution.
 * You may not use this file except in compliance with the License.
 */

package de.dfki.resc28.serendipity;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

import de.dfki.resc28.igraphstore.IGraphStore;
import de.dfki.resc28.igraphstore.jena.FusekiGraphStore;
import de.dfki.resc28.serendipity.services.Service;

@ApplicationPath("/")
public class Server extends Application
{
	public static IGraphStore fGraphStore;
	public static String fBaseURI;
	public static URI hostBaseUri;
	public static Resource serendipity;
	
	public Server(@Context ServletContext servletContext) throws URISyntaxException, IOException
	{
		configure();
	}


	@Override
    public Set<Object> getSingletons() 
    {	
		Service bla = new Service();
		return new HashSet<Object>(Arrays.asList(bla));
    }
	
    public static synchronized void configure() 
    {
        try 
        {
            String configFile = System.getProperty("serendipity.configuration");
            java.io.InputStream is;

            if (configFile != null) 
            {
                is = new java.io.FileInputStream(configFile);
                System.out.format("Loading serendipity configuration from %s ...%n", configFile);
            } 
            else 
            {
                is = Server.class.getClassLoader().getResourceAsStream("serendipity.properties");
                System.out.println("Loading serendipity configuration from internal resource file ...");
            }

            java.util.Properties p = new Properties();
            p.load(is);

            Server.fBaseURI = getProperty(p, "baseURI", "serendipity.baseURI");
			Server.serendipity = ResourceFactory.createResource(fBaseURI.toString());

            String storage = getProperty(p, "graphStore", "serendipity.graphStore");
            if (storage.equals("fuseki")) 
            {
                String dataEndpoint = getProperty(p, "dataEndpoint", "serendipity.dataEndpoint");
                String queryEndpoint = getProperty(p, "queryEndpoint", "serendipity.queryEndpoint");
                System.out.format("Use Fuseki backend:%n  dataEndpoint=%s%n  queryEndpoint=%s ...%n", dataEndpoint, queryEndpoint);

                Server.fGraphStore = new FusekiGraphStore(dataEndpoint, queryEndpoint);
            }
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
        }
    }

    public static String getProperty(java.util.Properties p, String key, String sysKey) 
    {
        String value = System.getProperty(sysKey);
        if (value != null) 
        {
            return value;
        }
        return p.getProperty(key);
    }
}
