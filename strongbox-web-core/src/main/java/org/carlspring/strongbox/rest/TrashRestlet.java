package org.carlspring.strongbox.rest;

import org.carlspring.strongbox.configuration.ConfigurationManager;
import org.carlspring.strongbox.services.ArtifactManagementService;
import org.carlspring.strongbox.storage.resolvers.ArtifactStorageException;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Martin Todorov
 */
@Component
@Path("/trash")
public class TrashRestlet
        extends BaseRestlet
{

    private static final Logger logger = LoggerFactory.getLogger(TrashRestlet.class);

    @Autowired
    private ArtifactManagementService artifactManagementService;

    @Autowired
    private ConfigurationManager configurationManager;


    @DELETE
    @Path("{storageId}/{repositoryId}")
    public Response delete(@PathParam("storageId") String storageId,
                           @PathParam("repositoryId") String repositoryId)
            throws IOException
    {
        if (configurationManager.getConfiguration().getStorage(storageId).getRepository(repositoryId) != null)
        {
            try
            {
                artifactManagementService.deleteTrash(storageId, repositoryId);

                logger.debug("Deleted trash for repository " + repositoryId + ".");
            }
            catch (ArtifactStorageException e)
            {
                throw new WebApplicationException(e, Response.Status.NOT_FOUND);
            }

            return Response.ok().build();
        }
        else
        {
            return Response.status(Response.Status.NOT_FOUND).entity("Storage or repository could not be found!").build();
        }
    }

    @DELETE
    public Response delete()
            throws IOException
    {
        try
        {
            artifactManagementService.deleteTrash();

            logger.debug("Deleted trash for all repositories.");
        }
        catch (ArtifactStorageException e)
        {
            throw new WebApplicationException(e, Response.Status.INTERNAL_SERVER_ERROR);
        }

        return Response.ok().build();
    }

    @POST
    @Path("{storageId}/{repositoryId}/{path:.*}")
    public Response undelete(@PathParam("storageId") String storageId,
                             @PathParam("repositoryId") String repositoryId,
                             @PathParam("path") String path)
            throws IOException
    {
        logger.debug("UNDELETE: " + path);
        logger.debug(storageId + ":" + repositoryId + ": " + path);

        try
        {
            artifactManagementService.undelete(storageId, repositoryId, path);

            logger.debug("Undeleted trash for path " + path + " under repository " + storageId + ":" + repositoryId + ".");
        }
        catch (ArtifactStorageException e)
        {
            throw new WebApplicationException(e, Response.Status.NOT_FOUND);
        }

        return Response.ok().build();
    }

    @POST
    @Path("{storageId}/{repositoryId}")
    public Response undelete(@PathParam("storageId") String storageId,
                             @PathParam("repositoryId") String repositoryId)
            throws IOException
    {
        if (configurationManager.getConfiguration().getStorage(storageId).getRepository(repositoryId) != null)
        {
            try
            {
                artifactManagementService.undeleteTrash(storageId, repositoryId);

                logger.debug("Undeleted trash for repository " + repositoryId + ".");
            }
            catch (ArtifactStorageException e)
            {
                throw new WebApplicationException(e, Response.Status.NOT_FOUND);
            }

            return Response.ok().build();
        }
        else
        {
            return Response.status(Response.Status.NOT_FOUND).entity("Storage or repository could not be found!").build();
        }
    }

    @POST
    public Response undelete()
            throws IOException
    {
        try
        {
            artifactManagementService.undeleteTrash();

            logger.debug("Undeleted trash for all repositories.");
        }
        catch (ArtifactStorageException e)
        {
            logger.error(e.getMessage(), e);

            throw new WebApplicationException(e, Response.Status.INTERNAL_SERVER_ERROR);
        }

        return Response.ok().build();
    }

}
