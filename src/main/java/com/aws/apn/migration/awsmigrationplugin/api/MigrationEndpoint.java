package com.aws.apn.migration.awsmigrationplugin.api;

import com.aws.apn.migration.awsmigrationplugin.spi.MigrationService;
import com.aws.apn.migration.awsmigrationplugin.spi.MigrationStage;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * REST API Endpoint for managing in-product DC migrations.
 * Supports get and create.
 */
@Path("/migration")
public class MigrationEndpoint {

    private MigrationService migrationService;

    public MigrationEndpoint(MigrationService migrationService) {
        this.migrationService = migrationService;
    }

    /**
     * @return A response with the status of the current migration
     */
    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMigrationStatus() {
        if (migrationService.getMigrationStage() == MigrationStage.UNSTARTED) {
            return Response
                    .status(Response.Status.NOT_FOUND)
                    .build();
        } else {
            return Response
                    .ok(migrationService.getMigrationStage().toString())
                    .build();
        }
    }

    /**
     * Creates a new migration if none exists. Otherwise will respond with a 400 and an error message.
     * @return no content if successful or 400 and error  message if a migration already exists.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createMigration() {
        if (migrationService.startMigration()) {
            return Response
                    .noContent()
                    .build();
        } else {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity("migration already exists")
                    .build();
        }
    }
}
