package org.intermine.bio.dataconversion;

import java.io.IOException;
import java.util.Map;

public interface TrialParser {

	Map<String, String> parse() throws IOException;

}