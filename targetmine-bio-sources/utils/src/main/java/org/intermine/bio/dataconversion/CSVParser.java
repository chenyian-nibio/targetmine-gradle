package org.intermine.bio.dataconversion;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class CSVParser implements Iterable<Map<String,String>>,AutoCloseable{
	private boolean multiColumnHeader = false; 
	private BufferedReader reader;
	
	public CSVParser(Reader reader,boolean multiColumnHeader) {
		super();
		this.reader = new BufferedReader(reader);
		this.multiColumnHeader = multiColumnHeader;
	}
	public CSVParser(Reader reader) {
		super();
		this.reader = new BufferedReader(reader);
	}
	@Override
	public void close() throws Exception {
		if(reader!=null) {
			reader.close();
			reader = null;
		}
	}
	private String[] headerTypes;
	private String[] headers;
	public Map<String,List<String>> getHeaderGroups(){
		HashMap<String, List<String>> map = new HashMap<>();
		for (int i = 0; i < headers.length; i++) {
			String type = headerTypes[Math.min(i, headerTypes.length-1)];
			List<String> list = map.get(type);
			if(list==null) {
				list = new ArrayList<String>();
				map.put(type, list);
			}
			list.add(headers[i]);
		}
		return map;
	}
	class RowIterator implements Iterator<Map<String, String>>{
		private Map<String, String> row;
		public RowIterator() {
			try {
				readHeaders();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		private void readHeaders() throws IOException {
			if(multiColumnHeader) {
				String readLine = reader.readLine();
				String[] cols = splitLine(readLine);
				String prevHeader = null;
				for (int i = 0; i < cols.length; i++) {
					if(Utils.empty(cols[i])) {
						cols[i] = prevHeader;
					}
					prevHeader = cols[i];
				}
				CSVParser.this.headerTypes = cols;
			}
			String readLine = reader.readLine();
			if(readLine==null) {
				throw new RuntimeException("No line");
			}
			headers = splitLine(readLine);
		}
		public boolean readLine() throws IOException {
			if(headers==null) {
			}
			String readLine = reader.readLine();
			if(readLine==null) {
				return false;
			}
			String[] cols = splitLine(readLine);
			row = new HashMap<String, String>();
			for (int i = 0; i < headers.length && i < cols.length; i++) {
				row.put(headers[i], cols[i]);
			}
			return true;
		}
		@Override
		public boolean hasNext() {
			try {
				return readLine();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		@Override
		public Map<String, String> next() {
			return row;
		}
	}
	@Override
	public Iterator<Map<String, String>> iterator() {
		return new RowIterator();
	}
	private static String[] splitLine(String line) {
		line = line.replaceFirst("^\"", "");
		line = line.replaceFirst("\"$", "");
		String[] split = line.split("\",\"");
		for (int i = 0; i < split.length; i++) {
			split[i] = split[i].replaceAll("\\\\n", "\n");
		}
		return split;
	}
	public static void main(String[] args) throws Exception {
		try(CSVParser reader = new CSVParser(new FileReader(new File(args[0])))){
			for (Map<String,String> map : reader) {
				System.out.println(map);
			}
		}
	}
}
