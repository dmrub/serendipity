/*
 * This file is part of serendipity. It is subject to the license terms in
 * the LICENSE file found in the top-level directory of this distribution.
 * You may not use this file except in compliance with the License.
 */
package de.dfki.resc28.serendipity.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.ext.Provider;
import javax.ws.rs.ext.WriterInterceptor;
import javax.ws.rs.ext.WriterInterceptorContext;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;

import de.dfki.resc28.igraphstore.Constants;
import de.dfki.resc28.serendipity.Server;
import de.dfki.resc28.serendipity.Util;

@Provider
@GenerateAffordances
public class RepresentationEnricher implements WriterInterceptor
{
	public void aroundWriteTo(WriterInterceptorContext context) throws IOException, WebApplicationException 
	{
		OutputStream originalStream = context.getOutputStream();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		context.setOutputStream(baos);
		
		try 
		{
            context.proceed();
        } 
		finally 
		{
			// get the original responseModel
            final Model responseModel = ModelFactory.createDefaultModel();
            final String contentType = context.getMediaType().toString();
            RDFDataMgr.read(responseModel, new StringReader(baos.toString()), "", RDFLanguages.contentTypeToLang(contentType));
            baos.close();            
            
            // ask serendipity for affordances to add
            Model affordances = ModelFactory.createDefaultModel();
            
            StringWriter writer = new StringWriter();
            responseModel.write(writer, Constants.CT_TEXT_TURTLE);
            StringEntity entity = new StringEntity(writer.toString(), ContentType.create(Constants.CT_TEXT_TURTLE));
            
            CloseableHttpClient client = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(Util.joinPath(Server.fBaseURI, "affordances"));
            httpPost.setHeader("Accept", Constants.CT_TEXT_TURTLE);
            httpPost.setHeader("Content-type", Constants.CT_TEXT_TURTLE);
            httpPost.setEntity(entity);
            
            CloseableHttpResponse response = client.execute(httpPost);

            RDFDataMgr.read(affordances, response.getEntity().getContent(), "", RDFLanguages.contentTypeToLang(response.getEntity().getContentType().getValue()));
            
            client.close();
            
            // add the instantiated action to the responseModel	
            responseModel.add(affordances);
                      
            // overwrite the response
            ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
            RDFDataMgr.write(baos2, responseModel, Lang.TURTLE);	// TODO: get the ACCEPT-Header value of the initial request to OLE
            
            baos2.writeTo(originalStream);
            baos2.close();
            context.setOutputStream(originalStream);
            
            
//            baos.writeTo(originalStream);
//            baos.close();
//            context.setOutputStream(originalStream);           
		}

	}
}