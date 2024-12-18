package utils;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

final public class JSON {
	final static ObjectMapper mapper = new ObjectMapper();

	synchronized public static final String encode(Object obj) {
		try {
			return mapper.writeValueAsString(obj);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			return "";
		}
	}

	synchronized public static final <T> T decode(String json, Class<T> classOf) {
		try {
			var res = mapper.readValue(json, classOf);
			return res;
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			return null;
		}
	}

	synchronized public static final <T> T decode(String json, TypeReference<T> typeOf) {
		try {
			var res = mapper.readValue(json, typeOf);
			return res;
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			return null;
		}
	}

	// New method to decode JSON arrays into List<T> (this method was made with the help of AI)
	synchronized public static final <T> List<T> decodeList(String json, Class<T> elementClass) {
		try {
			return mapper.readValue(json, mapper.getTypeFactory().constructCollectionType(List.class, elementClass));
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			return null;
		}
	}
}