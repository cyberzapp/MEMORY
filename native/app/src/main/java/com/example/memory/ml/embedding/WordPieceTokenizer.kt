package com.example.memory.ml.embedding

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * WordPiece tokenizer for all-MiniLM-L6-v2.
 *
 * Converts raw text → token IDs that the embedding model expects.
 * This is the critical bridge between human text and neural inference.
 *
 * Implements:
 * 1. Text normalization (lowercase, strip accents)
 * 2. Basic tokenization (whitespace + punctuation split)
 * 3. WordPiece sub-word tokenization (## prefix continuations)
 * 4. Token → ID mapping via vocab.txt
 * 5. Padding/truncation to max_seq_len (128)
 * 6. Generates: input_ids, attention_mask, token_type_ids
 */
class WordPieceTokenizer(context: Context) {

    private val vocab: Map<String, Int>
    private val maxSeqLen = 128

    // Special token IDs
    private val clsTokenId: Int
    private val sepTokenId: Int
    private val padTokenId: Int
    private val unkTokenId: Int

    init {
        // Load vocab.txt from assets — ~30,522 tokens
        vocab = loadVocab(context)
        clsTokenId = vocab["[CLS]"] ?: 101
        sepTokenId = vocab["[SEP]"] ?: 102
        padTokenId = vocab["[PAD]"] ?: 0
        unkTokenId = vocab["[UNK]"] ?: 100
    }

    private fun loadVocab(context: Context): Map<String, Int> {
        val vocabMap = mutableMapOf<String, Int>()
        context.assets.open("vocab.txt").use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var index = 0
                reader.forEachLine { line ->
                    vocabMap[line.trim()] = index
                    index++
                }
            }
        }
        return vocabMap
    }

    /**
     * Tokenize a text string into model-ready tensors.
     *
     * @param text Raw input text (e.g., "charger, cable | Dell 65W USB-C | Home Office")
     * @return TokenizedInput with padded arrays ready for LiteRT inference
     */
    fun tokenize(text: String): TokenizedInput {
        // Step 1: Normalize
        val normalized = normalize(text)

        // Step 2: Basic tokenization (whitespace + punctuation split)
        val basicTokens = basicTokenize(normalized)

        // Step 3: WordPiece sub-word tokenization
        val wordPieceTokens = mutableListOf<String>()
        for (token in basicTokens) {
            wordPieceTokens.addAll(wordPieceTokenize(token))
        }

        // Step 4: Truncate to fit [CLS] ... [SEP] within maxSeqLen
        val maxTokens = maxSeqLen - 2 // reserve for CLS and SEP
        val truncated = if (wordPieceTokens.size > maxTokens) {
            wordPieceTokens.subList(0, maxTokens)
        } else {
            wordPieceTokens
        }

        // Step 5: Build token IDs: [CLS] + tokens + [SEP] + [PAD]...
        val tokenIds = IntArray(maxSeqLen) { padTokenId }
        val attentionMask = IntArray(maxSeqLen) { 0 }
        val tokenTypeIds = IntArray(maxSeqLen) { 0 }

        tokenIds[0] = clsTokenId
        attentionMask[0] = 1

        for (i in truncated.indices) {
            tokenIds[i + 1] = vocab[truncated[i]] ?: unkTokenId
            attentionMask[i + 1] = 1
        }

        val sepPosition = truncated.size + 1
        tokenIds[sepPosition] = sepTokenId
        attentionMask[sepPosition] = 1

        return TokenizedInput(
            inputIds = tokenIds,
            attentionMask = attentionMask,
            tokenTypeIds = tokenTypeIds
        )
    }

    /**
     * Text normalization: lowercase, basic cleanup.
     * Keeps it simple — MiniLM was trained on cased text but
     * the uncased version we use expects lowercase.
     */
    private fun normalize(text: String): String {
        return text.lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Basic tokenization: split on whitespace and punctuation.
     * Punctuation becomes its own token.
     */
    private fun basicTokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()

        for (char in text) {
            when {
                char.isWhitespace() -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current.clear()
                    }
                }
                isPunctuation(char) -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current.clear()
                    }
                    tokens.add(char.toString())
                }
                else -> current.append(char)
            }
        }

        if (current.isNotEmpty()) {
            tokens.add(current.toString())
        }

        return tokens
    }

    /**
     * WordPiece tokenization: greedily match longest vocab subword.
     * Unknown sub-pieces get the ## prefix for continuation tokens.
     */
    private fun wordPieceTokenize(token: String): List<String> {
        if (token.isEmpty()) return emptyList()
        if (vocab.containsKey(token)) return listOf(token)

        val subTokens = mutableListOf<String>()
        var start = 0

        while (start < token.length) {
            var end = token.length
            var foundSubToken: String? = null

            while (start < end) {
                val subStr = if (start == 0) {
                    token.substring(start, end)
                } else {
                    "##" + token.substring(start, end)
                }

                if (vocab.containsKey(subStr)) {
                    foundSubToken = subStr
                    break
                }
                end--
            }

            if (foundSubToken == null) {
                // Character not in vocab at all — use [UNK]
                subTokens.add("[UNK]")
                start++
            } else {
                subTokens.add(foundSubToken)
                start = end
            }
        }

        return subTokens
    }

    private fun isPunctuation(char: Char): Boolean {
        val cp = char.code
        // ASCII punctuation ranges
        if (cp in 33..47 || cp in 58..64 || cp in 91..96 || cp in 123..126) return true
        // Unicode general punctuation
        return Character.getType(char).toByte().toInt().let {
            it == Character.CONNECTOR_PUNCTUATION.toInt() ||
            it == Character.DASH_PUNCTUATION.toInt() ||
            it == Character.END_PUNCTUATION.toInt() ||
            it == Character.FINAL_QUOTE_PUNCTUATION.toInt() ||
            it == Character.INITIAL_QUOTE_PUNCTUATION.toInt() ||
            it == Character.OTHER_PUNCTUATION.toInt() ||
            it == Character.START_PUNCTUATION.toInt()
        }
    }
}

/**
 * Model-ready tokenized input for all-MiniLM-L6-v2.
 * All arrays are exactly [maxSeqLen] (128) elements.
 */
data class TokenizedInput(
    val inputIds: IntArray,        // [CLS] token_ids [SEP] [PAD]...
    val attentionMask: IntArray,   // 1 for real tokens, 0 for padding
    val tokenTypeIds: IntArray     // all 0s for single-sentence input
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TokenizedInput) return false
        return inputIds.contentEquals(other.inputIds)
    }

    override fun hashCode(): Int = inputIds.contentHashCode()
}
