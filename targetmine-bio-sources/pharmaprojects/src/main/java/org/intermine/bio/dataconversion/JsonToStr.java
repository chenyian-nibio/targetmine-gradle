package org.intermine.bio.dataconversion;

import org.json.JSONArray;
import org.json.JSONObject;

public class JsonToStr {
	private String key;
	private String template;
	private String delim = ", ";
	
	public JsonToStr(String key) {
		this.key = key;
	}

	public JsonToStr(String key, String template) {
		this.key = key;
		this.template = template;
	}
	
	public JsonToStr(String key, String template, String delim) {
		this.key = key;
		this.template = template;
		this.delim = delim;
	}

	public String toString(JSONObject object) {
		return objToString(object.opt(key));
	}
	public String objToString(Object object) {
		if(object==null) {
			return null;
		}
		if (object instanceof String) {
			return (String) object;
		}
		if (object instanceof JSONArray) {
			StringBuilder sb = new StringBuilder();
			JSONArray array = (JSONArray) object;
			for (int i = 0; i < array.length(); i++) {
				if(i>0) {
					sb.append(delim);
				}
				sb.append(objToString(array.get(i)));
			}
			return sb.toString();
		}
		if (object instanceof JSONObject) {
			JSONObject obj = (JSONObject) object;
			if(template==null) {
				throw new RuntimeException("template is null for "+ key);
			}

			return Utils.replaceString(template, obj);
		}
		return object.toString();
	}
}
