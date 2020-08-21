package org.intermine.bio.dataconversion;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;

public class Utils {
	private static final Pattern KeyStringPattern = Pattern.compile("([^/]+)/([^/]+)/([^/]*)");
	private static final Pattern replaceKeyPattern = Pattern.compile("\\$\\{([^}]+)\\}");
	public static String replaceString(String template,Map<String,? extends Object> variables){
		StringBuilder sb = new StringBuilder();
		Matcher matcher = replaceKeyPattern.matcher(template);
		int prevPos = 0;
		while(matcher.find()){
			sb.append(template.substring(prevPos, matcher.start()));
			String key = matcher.group(1);
			Matcher matcher2 = KeyStringPattern.matcher(key);
			String replacePattern = null;
			String replaceString = null;
			if(matcher2.matches()){
				key = matcher2.group(1);
				replacePattern = matcher2.group(2);
				replaceString = matcher2.group(3);
			}
			Object object = variables.get(key);
			String str = object!=null?object.toString():matcher.group(0);
			if(replacePattern!=null){
				str = str.replaceFirst(replacePattern, replaceString);
			}
			sb.append(str);
			prevPos = matcher.end();
		}
		sb.append(template.substring(prevPos));
		return sb.toString();
	}
	public static String replaceString(String template,JSONObject variables){
		StringBuilder sb = new StringBuilder();
		Matcher matcher = replaceKeyPattern.matcher(template);
		int prevPos = 0;
		while(matcher.find()){
			sb.append(template.substring(prevPos, matcher.start()));
			String key = matcher.group(1);
			Matcher matcher2 = KeyStringPattern.matcher(key);
			String replacePattern = null;
			String replaceString = null;
			if(matcher2.matches()){
				key = matcher2.group(1);
				replacePattern = matcher2.group(2);
				replaceString = matcher2.group(3);
			}
			Object object = variables.opt(key);
			String str = object!=null?object.toString():matcher.group(0);
			if(replacePattern!=null){
				str = str.replaceFirst(replacePattern, replaceString);
			}
			sb.append(str);
			prevPos = matcher.end();
		}
		sb.append(template.substring(prevPos));
		return sb.toString();
	}
	public static void main(String[] args) {
		JSONObject variables = new JSONObject();
		variables.put("test", "value");
		System.out.println(replaceString(null, variables));
	}
	public static boolean empty(String string) {
		return string == null || string.length() <= 0;
	}
	public static boolean isEmpty(String entrezGeneId) {
		return entrezGeneId == null || entrezGeneId.length() <=0;
	}
}
