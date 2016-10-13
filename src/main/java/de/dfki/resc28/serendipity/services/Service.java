/*
 * This file is part of serendipity. It is subject to the license terms in
 * the LICENSE file found in the top-level directory of this distribution.
 * You may not use this file except in compliance with the License.
 */

package de.dfki.resc28.serendipity.services;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.core.Response.Status;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.Syntax;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.rdf.model.RDFList.ApplyFn;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.core.Prologue;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.topbraid.spin.arq.ARQ2SPIN;
import org.topbraid.spin.arq.ARQFactory;
import org.topbraid.spin.model.Construct;
import org.topbraid.spin.model.Element;
import org.topbraid.spin.model.Function;
import org.topbraid.spin.model.SPINFactory;
import org.topbraid.spin.model.SPINResourceImpl;
import org.topbraid.spin.model.TriplePattern;
import org.topbraid.spin.system.SPINModuleRegistry;
import org.topbraid.spin.util.JenaUtil;
import org.topbraid.spin.vocabulary.SP;

import de.dfki.resc28.igraphstore.Constants;
import de.dfki.resc28.serendipity.Server;

@Path("/")
public class Service 
{
	@Context protected UriInfo fRequestUrl;
	
	@GET
	@Path("recipes")
	@Produces({ Constants.CT_APPLICATION_JSON_LD, Constants.CT_APPLICATION_NQUADS, Constants.CT_APPLICATION_NTRIPLES, Constants.CT_APPLICATION_RDF_JSON, Constants.CT_APPLICATION_RDFXML, Constants.CT_APPLICATION_TRIX, Constants.CT_APPLICATION_XTURTLE, Constants.CT_TEXT_N3, Constants.CT_TEXT_TRIG, Constants.CT_TEXT_TURTLE })
	public Response listProviders( @HeaderParam(HttpHeaders.ACCEPT)  @DefaultValue(Constants.CT_TEXT_TURTLE) final String acceptType )
	{		
		StreamingOutput out = new StreamingOutput() 
		{
			public void write(OutputStream output) throws IOException, WebApplicationException
			{
				RDFDataMgr.write(output, Server.fGraphStore.getDefaultGraph(), RDFDataMgr.determineLang(null, acceptType, null)) ;
			}
		};
		
		return Response.ok(out).type(acceptType).build();
	}

	@GET
	@Path("recipes/{recipeUri: .+}")
	@Produces({ Constants.CT_APPLICATION_JSON_LD, Constants.CT_APPLICATION_NQUADS, Constants.CT_APPLICATION_NTRIPLES, Constants.CT_APPLICATION_RDF_JSON, Constants.CT_APPLICATION_RDFXML, Constants.CT_APPLICATION_TRIX, Constants.CT_APPLICATION_XTURTLE, Constants.CT_TEXT_N3, Constants.CT_TEXT_TRIG, Constants.CT_TEXT_TURTLE })
	public Response showProvider( @PathParam("recipeUri") final String recipeUri ,
								  @HeaderParam(HttpHeaders.ACCEPT)  @DefaultValue(Constants.CT_TEXT_TURTLE) final String acceptType )
	{
		if (!Server.fGraphStore.containsNamedGraph(recipeUri))
		{
			return Response.status(Status.NOT_FOUND).build();
		}
		else
		{			
			StreamingOutput out = new StreamingOutput() 
			{
				public void write(OutputStream output) throws IOException, WebApplicationException
				{
					RDFDataMgr.write(output, Server.fGraphStore.getNamedGraph(recipeUri), RDFDataMgr.determineLang(null, acceptType, null)) ;
				}
			};
		
			return Response.ok(out).type(acceptType).build();
		}
	}
	
	@POST
	@Path("recipes")
	public Response addRecipe( @QueryParam("uri") String recipeUri )
	{
		try 
		{
			// load the recipe
			Model recipeModel = ModelFactory.createDefaultModel();
			RDFDataMgr.read(recipeModel, recipeUri, Lang.TURTLE);
			Server.fGraphStore.createNamedGraph(recipeUri, recipeModel);
			
			Model recipesModel = Server.fGraphStore.getDefaultGraph();
			recipesModel.add(Server.serendipity, RDFS.member, ResourceFactory.createResource(recipeUri));
			Server.fGraphStore.addToDefaultGraph(recipesModel);
			
			
			return Response.created(new URI(recipeUri)).build();
		
		} 
		catch (UnsupportedOperationException e) 
		{
			e.printStackTrace();
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		} 
		catch (URISyntaxException e) 
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
			return Response.status(Status.BAD_REQUEST).build();
		}
		
		
	}

	@DELETE
	@Path("recipes/{recipeUri: .+}")
	public Response deregisterProvider( @PathParam("recipeUri") String recipeUri )
	{
		if (!Server.fGraphStore.containsNamedGraph(recipeUri))
		{
			return Response.status(Status.NOT_FOUND).build();
		}
		else
		{
			Model recipesModel = Server.fGraphStore.getDefaultGraph();
			recipesModel.remove(recipesModel.listStatements(null, RDFS.member, ResourceFactory.createResource(recipeUri)));
			Server.fGraphStore.replaceDefaultGraph(recipesModel);
			
			Server.fGraphStore.deleteNamedGraph(recipeUri);
			
			return Response.status(Status.NO_CONTENT).build();
		}
	}
	
	@POST
	@Path("affordances")
	@Produces({ Constants.CT_APPLICATION_JSON_LD, Constants.CT_APPLICATION_NQUADS, Constants.CT_APPLICATION_NTRIPLES, Constants.CT_APPLICATION_RDF_JSON, Constants.CT_APPLICATION_RDFXML, Constants.CT_APPLICATION_TRIX, Constants.CT_APPLICATION_XTURTLE, Constants.CT_TEXT_N3, Constants.CT_TEXT_TRIG, Constants.CT_TEXT_TURTLE })
	public Response generateAffordances( InputStream content, 
										 @HeaderParam(HttpHeaders.CONTENT_TYPE) final String contentType,
										 @HeaderParam(HttpHeaders.ACCEPT)  @DefaultValue(Constants.CT_TEXT_TURTLE) final String acceptType )
	{
		SPINModuleRegistry.get().init();
		
		// get model to enrich
		final Model modelToEnrich = ModelFactory.createDefaultModel();
		RDFDataMgr.read(modelToEnrich, content, null, RDFLanguages.contentTypeToLang(contentType));
		
		RDFDataMgr.write(System.out, modelToEnrich, RDFLanguages.contentTypeToLang(contentType));
		
		
		// prepare a model for all generated affordances
		final Model affordanceModel = ModelFactory.createDefaultModel();
		
		// prepare a model that the affordanceGenerator works on
		final Model workingModel = ModelFactory.createDefaultModel();
		workingModel.add(modelToEnrich);
		
		// iterate IN ORDER over all registered recipes
		RDFDataMgr.write(System.out,  Server.fGraphStore.getDefaultGraph(), Lang.TURTLE); 
		
		NodeIterator recipeListIterator = Server.fGraphStore.getDefaultGraph().listObjectsOfProperty(RDFS.member);
		while(recipeListIterator.hasNext())
		{
			System.out.println("Found a recipeList!");
			RDFList recipeList = (RDFList) recipeListIterator.next();
			System.out.println(recipeList.getURI());
			ApplyFn generate = new ApplyFn() 
			{
				public void apply(RDFNode recipe) 
				{
					workingModel.add(affordanceModel);
					
					String recipeUri = recipe.asResource().getURI();
		        	Model recipeModel = Server.fGraphStore.getNamedGraph(recipeUri);
		        	
		        	System.out.println(recipeUri);
		        	
		        	Resource queryInstance = SPINFactory.asQuery(recipeModel.listResourcesWithProperty(RDF.type, SP.Construct).next());       	
		        	org.apache.jena.query.Query arq = ARQFactory.get().createQuery((Construct) queryInstance);
		        	PrefixMapping pfxMap = PrefixMapping.Factory.create();
		        	pfxMap.setNsPrefixes(workingModel.getNsPrefixMap());
		        	pfxMap.setNsPrefixes(recipeModel.getNsPrefixMap());
		        	arq.usePrologueFrom(new Prologue(pfxMap));
		        	
		    		QueryExecution qexec = ARQFactory.get().createQueryExecution(arq, workingModel);

		    		Model affordances = JenaUtil.createDefaultModel();
		    		affordances = qexec.execConstruct();
		    		
		    		affordanceModel.add(affordances);
				}
			};
			recipeList.apply(generate);
		}
		
//		NodeIterator recipeIterator = Server.fGraphStore.getDefaultGraph().listObjectsOfProperty(RDFS.member);
//		while (recipeIterator.hasNext())
//		{
//			workingModel.add(affordanceModel);
//			
//			String recipeUri = recipeIterator.next().asResource().getURI();
//        	Model recipeModel = Server.fGraphStore.getNamedGraph(recipeUri);
//        	
//        	System.out.println(recipeUri);
//        	
//        	Resource queryInstance = SPINFactory.asQuery(recipeModel.listResourcesWithProperty(RDF.type, SP.Construct).next());       	
//        	org.apache.jena.query.Query arq = ARQFactory.get().createQuery((Construct) queryInstance);
//        	PrefixMapping pfxMap = PrefixMapping.Factory.create();
//        	pfxMap.setNsPrefixes(workingModel.getNsPrefixMap());
//        	pfxMap.setNsPrefixes(recipeModel.getNsPrefixMap());
//        	arq.usePrologueFrom(new Prologue(pfxMap));
//        	
//    		QueryExecution qexec = ARQFactory.get().createQueryExecution(arq, workingModel);
//
//    		Model affordances = JenaUtil.createDefaultModel();
//    		affordances = qexec.execConstruct();
//    		
//    		affordanceModel.add(affordances);
//		}
		
		StreamingOutput out = new StreamingOutput() 
		{
			public void write(OutputStream output) throws IOException, WebApplicationException
			{
				RDFDataMgr.write(output, affordanceModel, RDFDataMgr.determineLang(null, acceptType, null)) ;
			}
		};
	
		return Response.ok(out).type(acceptType).build();
	}
}
