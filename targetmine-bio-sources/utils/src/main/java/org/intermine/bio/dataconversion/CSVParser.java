package org.intermine.bio.dataconversion;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class CSVParser implements Iterable<Map<String,String>>,AutoCloseable{
	private BufferedReader reader;
	
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
	class RowIterator implements Iterator<Map<String, String>>{
		private String[] headers;
		private Map<String, String> row;
		public boolean readLine() throws IOException {
			if(headers==null) {
				String readLine = reader.readLine();
				if(readLine==null) {
					throw new RuntimeException("No line");
				}
				headers = splitLine(readLine);
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
