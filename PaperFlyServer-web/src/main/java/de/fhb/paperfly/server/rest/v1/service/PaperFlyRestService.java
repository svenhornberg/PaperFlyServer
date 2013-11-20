package de.fhb.paperfly.server.rest.v1.service;

import de.fhb.paperfly.server.logging.service.LoggingServiceLocal;
import de.fhb.paperfly.server.rest.v1.mapping.ToDTOMapper;
import de.fhb.paperfly.server.rest.v1.mapping.ToEntityMapper;
import de.fhb.paperfly.server.rest.v1.service.resources.account.AccountResource;
import de.fhb.paperfly.server.rest.v1.service.resources.auth.AuthResource;
import de.fhb.paperfly.server.rest.v1.service.resources.room.RoomResource;
import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ejb.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

/**
 * Address: http://localhost:8080/PaperFlyServer-web/rest/v1
 *
 * @author MacYser
 */
@Path("/v1")
@Singleton
//@Interceptors({ServiceLoggerInterceptor.class})
public class PaperFlyRestService {

	@EJB
	public LoggingServiceLocal LOG;
	public static final String JSON_MEDIA_TYPE = "application/json;charset=utf-8";
	public static ToDTOMapper toDTOMapper;
	public static ToEntityMapper toEntityMapper;
	// Resources
	@EJB
	private AccountResource accountResource;
	@EJB
	private AuthResource authResource;
	@EJB
	private RoomResource roomResource;

	public PaperFlyRestService() {
		toDTOMapper = new ToDTOMapper();
		toEntityMapper = new ToEntityMapper();
	}

	@PostConstruct
	private void init() {
	}

	/**
	 * [LARGE]
	 *
	 * @title Ping the Service
	 * @summary Pings the Service to test if it is online.
	 * @return Returns an alive-message.
	 */
	@GET
	@Path("ping")
	public String ping() {
		return "alive";
	}

	@Path("/")
	public AuthResource getAuthResource() {
		return authResource;
	}

	@Path("/")
	public AccountResource getAccountResource() {
		return accountResource;
	}

	@Path("/")
	public RoomResource getRoomResource() {
		return roomResource;
	}

	public LoggingServiceLocal getLOG() {
		return LOG;
	}
}
