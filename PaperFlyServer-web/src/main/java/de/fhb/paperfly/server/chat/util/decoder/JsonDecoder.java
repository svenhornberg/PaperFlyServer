package de.fhb.paperfly.server.chat.util.decoder;

import com.google.gson.Gson;
import de.fhb.paperfly.server.chat.Message;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.security.Principal;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.json.Json;
import javax.json.JsonReader;
import javax.json.JsonStructure;
import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.EndpointConfig;

/**
 *
 * @author MacYser
 */
public class JsonDecoder implements Decoder.Text<Message> {

	private static final Logger LOG = Logger.getLogger(JsonDecoder.class.getName());
	private Gson gson;
	private Map<String, Object> userProperties;

	@Override
	public Message decode(String s) throws DecodeException {
		try {
			LOG.log(Level.INFO, "JsonDecoder decode");
			Message msg = gson.fromJson(s, Message.class);
			msg.setUsername((String) userProperties.get("username"));
			return msg;
		} catch (Exception ex) {
			Logger.getLogger(JsonDecoder.class.getName()).log(Level.SEVERE, null, ex);
		}
		return null;
	}

	@Override
	public boolean willDecode(String s) {
		LOG.log(Level.INFO, "JsonDecoder willDecode");
		boolean canDecode = true;
		return canDecode;
	}

	@Override
	public void init(EndpointConfig config) {
		LOG.log(Level.INFO, "Init JsonDecoder");
		gson = new Gson();
		userProperties = config.getUserProperties();
	}

	@Override
	public void destroy() {
		LOG.log(Level.INFO, "Destroying JsonDecoder");
		gson = null;
	}
}