package com.allende.filesearch.index;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.es.SpanishLightStemFilter;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;

/**
 * Analyzer for Spanish legal text: lowercases, drops Spanish stop words,
 * reduces plurals and inflections ("desahucios" matches "desahucio") and
 * ignores accents ("clausula" matches "cláusula").
 * The same analyzer is used at index and query time.
 */
public final class SpanishTextAnalyzer extends Analyzer {

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer source = new StandardTokenizer();
        TokenStream result = new LowerCaseFilter(source);
        result = new StopFilter(result, SpanishAnalyzer.getDefaultStopSet());
        result = new SpanishLightStemFilter(result);
        result = new ASCIIFoldingFilter(result);
        return new TokenStreamComponents(source, result);
    }

    @Override
    protected TokenStream normalize(String fieldName, TokenStream in) {
        return new ASCIIFoldingFilter(new LowerCaseFilter(in));
    }
}
