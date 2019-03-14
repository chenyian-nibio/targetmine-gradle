package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2018 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */


import java.io.IOException;
import java.io.Reader;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * @author Motokazu Ishikawa
 **/
public final class GostarFileParser
{
	private static final char DEFAULT_QUOTE = '"';

	private GostarFileParser() {
	}

	public static Iterator<String[]> parseCsvDelimitedReader(final BufferedReader reader)
		throws IOException {
		return parseDelimitedReader(reader, ',', DEFAULT_QUOTE);
	}

	private static Iterator<String[]> parseDelimitedReader(final BufferedReader reader, final char delim,
			char quoteChar) throws IOException {

		return new Iterator<String[]>() {
			String[] currentLine = null;
			{
				currentLine = getNextNonCommentLine();
			}

			@Override
			public boolean hasNext() {
				return currentLine != null;
			}

			@Override
			public String[] next() {
				if (currentLine == null) {
					throw new NoSuchElementException();
				}
				String[] lastLine = currentLine;

				try {
					currentLine = getNextNonCommentLine();
				} catch (IOException e) {
					throw new RuntimeException("error while reading from " + reader, e);
				}
				return lastLine;
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
			
			private String[] getNextNonCommentLine() throws IOException {
				StringBuffer line = null;
				//System.out.println(reader.readLine() );
				while (reader.ready()) {
					String readline = reader.readLine();
					if( readline == null ) {
						return null;
					}
					line = new StringBuffer( readline );
					boolean isInsideDoubleQuotes = false;
					for( int i = 0; i < line.length(); i++ ) {
						
						char currentChar = line.charAt( i );
						if( currentChar == '"' && ( i == 0 || line.charAt( i - 1 ) == '\t') ) {
							
							isInsideDoubleQuotes = true;
							
						}else if( currentChar == '"' && i != line.length() -1 && line.charAt( i + 1 ) == ',' ) {
							
							isInsideDoubleQuotes = false;
							
						}else if( currentChar == ',' && ! isInsideDoubleQuotes ) {
							
							line.setCharAt( i, '\t' );
							
						}
						
					}
					String[] fields = line.toString().split( "\t" );
					
					// cut "" if they reside on both sides
					for( int i = 0; i < fields.length; i++ ) {
						if( 2 < fields[i].length() && fields[i].charAt(0) == '"' && fields[i].charAt(fields[i].length() - 1) == '"' ) {
							fields[i] = fields[i].substring( 1, fields[i].length() - 1 );
						}
					}
					
					if (fields[0] != null && fields[0].startsWith("#")) {
						// skip comments, go to next line
						continue;
					}
					// legal line
					return fields;
				}
				return null;
			}
		};
	}
	
	public static final void main( String[] args ){
		
		String testString = "\"Mitomycin\",MCD,101893,\"[(4S,6S,7R,8S)-11-amino-7-methoxy-12-methyl-10,13-dioxo-2,5-diazatetracyclo[7.4.0.0{2,7}.0{4,6}]trideca-1(9),11-dien-8-yl]methyl carbamate\",C15H18N4O5,334.3272,,CO[C@@]12[C@H](COC(N)=O)C3=C(N1C[C@@H]4N[C@H]24)C(=O)C(C)=C(N)C3=O,,e03944722f855aa45d868956d6c6d997683b02f4ca063296f433fe75be0f0766,,1765054,\r"
				+ ",MCD,101896,\"dimethyl {[2-(6-amino-9H-purin-9-yl)ethoxy]methyl}phosphonate\",C10H16N5O4P,301.2389,,COP(=O)(COCCN1C=NC2=C(N)N=CN=C12)OC,,712f6e6939f7a61f436016534f38e0e84c1fa9191fd90af19a8e0e925e08f5ad,,1416611,";
		BufferedReader reader = new BufferedReader( new StringReader( testString ) );
		try {
			
			Iterator<String[]> itr = GostarFileParser.parseCsvDelimitedReader( reader );
			while( itr.hasNext() ) {
				String[] fields = itr.next();
				for( int i = 0; i < fields.length; i++ ) {
					System.out.println( i + " " + fields[ i ] );
				}
			}
			
		}catch( Exception e ){
			e.printStackTrace();
		}
		
	}
}
