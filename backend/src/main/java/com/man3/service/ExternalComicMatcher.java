package com.man3.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.man3.entity.Book;
import com.man3.entity.ExternalComic;
import com.man3.entity.NiaoniaomhComic;
import com.man3.entity.YuyumhComic;
import com.man3.mapper.BookMapper;
import com.man3.mapper.NiaoniaomhMapper;
import com.man3.mapper.YuyumhMapper;
import com.man3.utils.ComicNameNormalizer;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Matches external comics against local titles, including simplified/traditional variants. */
@Service
public class ExternalComicMatcher {

    private final BookMapper bookMapper;
    private final NiaoniaomhMapper niaoniaomhMapper;
    private final YuyumhMapper yuyumhMapper;

    public ExternalComicMatcher(BookMapper bookMapper, NiaoniaomhMapper niaoniaomhMapper,
                                YuyumhMapper yuyumhMapper) {
        this.bookMapper = bookMapper;
        this.niaoniaomhMapper = niaoniaomhMapper;
        this.yuyumhMapper = yuyumhMapper;
    }

    public void refreshNiaoniaomhMatches() {
        LibraryNameIndex libraryNames = libraryNameIndex();
        List<NiaoniaomhComic> list = niaoniaomhMapper.selectList(new LambdaQueryWrapper<NiaoniaomhComic>());
        for (NiaoniaomhComic comic : list) {
            updateMatch(comic, libraryNames);
            niaoniaomhMapper.updateById(comic);
        }
    }

    public void refreshYuyumhMatches() {
        LibraryNameIndex libraryNames = libraryNameIndex();
        List<YuyumhComic> list = yuyumhMapper.selectList(new LambdaQueryWrapper<YuyumhComic>());
        for (YuyumhComic comic : list) {
            updateMatch(comic, libraryNames);
            yuyumhMapper.updateById(comic);
        }
    }

    private LibraryNameIndex libraryNameIndex() {
        LibraryNameIndex result = new LibraryNameIndex();
        List<Book> books = bookMapper.selectList(new LambdaQueryWrapper<Book>()
                .select(Book::getId, Book::getName, Book::getNamePinyin));
        for (Book book : books) {
            String strictKey = normalizeName(book.getName());
            if (!strictKey.isEmpty()) result.strictNames.putIfAbsent(strictKey, book.getId());

            String pinyinKey = ComicNameNormalizer.toPinyin(book.getName());
            if (!pinyinKey.isEmpty()) {
                result.pinyinNames.computeIfAbsent(pinyinKey, ignored -> new ArrayList<Long>()).add(book.getId());
            }
            if (!pinyinKey.equals(book.getNamePinyin())) {
                book.setNamePinyin(pinyinKey);
                bookMapper.updateById(book);
            }
        }
        return result;
    }

    private void updateMatch(ExternalComic comic, LibraryNameIndex libraryNames) {
        String pinyinKey = ComicNameNormalizer.toPinyin(comic.getName());
        comic.setNamePinyin(pinyinKey);

        Long bookId = libraryNames.strictNames.get(normalizeName(comic.getName()));
        if (bookId == null) {
            List<Long> candidates = libraryNames.pinyinNames.get(pinyinKey);
            // Pinyin is a fallback for script variants. Do not auto-match ambiguous homophones.
            if (candidates != null && candidates.size() == 1) {
                bookId = candidates.get(0);
            }
        }
        comic.setIsSame(bookId == null ? 0 : 1);
        comic.setMatchedBookId(bookId);
    }

    /** Removes spacing and title punctuation while retaining the Chinese script itself. */
    public static String normalizeName(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .trim();
        return normalized.replaceAll("[\\s\\p{Punct}，。！？、】【（）《》【】〔〕〈〉「」『』]", "");
    }

    private static final class LibraryNameIndex {
        private final Map<String, Long> strictNames = new HashMap<>();
        private final Map<String, List<Long>> pinyinNames = new HashMap<>();
    }
}
