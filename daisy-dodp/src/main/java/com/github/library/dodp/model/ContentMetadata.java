package com.github.library.dodp.model;

import com.github.library.dodp.util.XmlUtil;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

import static com.github.library.dodp.DodpNamespaces.DAISY_ONLINE;
import static com.github.library.dodp.DodpNamespaces.DC;

/**
 * Metadata returned by {@code getContentMetadata}. Carries the
 * {@code requiresReturn} flag that distinguishes the lending model from the
 * purchase model, plus Dublin Core based descriptive metadata.
 */
public final class ContentMetadata {

    private final String category;
    private final boolean requiresReturn;
    private final String sampleId;
    private final Metadata metadata;

    public ContentMetadata(String category, boolean requiresReturn, String sampleId, Metadata metadata) {
        this.category = category;
        this.requiresReturn = requiresReturn;
        this.sampleId = sampleId;
        this.metadata = metadata;
    }

    public String getCategory() {
        return category;
    }

    public boolean isRequiresReturn() {
        return requiresReturn;
    }

    public String getSampleId() {
        return sampleId;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public ContentModel getContentModel() {
        return ContentModel.fromRequiresReturn(requiresReturn);
    }

    public static ContentMetadata parse(Element element) {
        String category = XmlUtil.attribute(element, "category");
        boolean requiresReturn = XmlUtil.parseBoolean(XmlUtil.attribute(element, "requiresReturn"));

        Element sample = XmlUtil.firstChildElement(element, DAISY_ONLINE, "sample");
        String sampleId = sample == null ? null : XmlUtil.attribute(sample, "id");

        Element metadataElement = XmlUtil.firstChildElement(element, DAISY_ONLINE, "metadata");
        Metadata metadata = metadataElement == null ? Metadata.EMPTY : Metadata.parse(metadataElement);
        return new ContentMetadata(category, requiresReturn, sampleId, metadata);
    }

    /**
     * Descriptive (Dublin Core based) metadata for a content item.
     */
    public static final class Metadata {

        static final Metadata EMPTY = new Metadata(null, null, null, null, null, null,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), null, Collections.emptyList());

        private final String title;
        private final String identifier;
        private final String publisher;
        private final String format;
        private final String date;
        private final String source;
        private final List<String> types;
        private final List<String> subjects;
        private final List<String> rights;
        private final List<String> relations;
        private final List<String> languages;
        private final List<String> descriptions;
        private final List<String> creators;
        private final List<String> coverages;
        private final List<String> contributors;
        private final List<String> narrators;
        private final Long size;
        private final List<Meta> metas;

        public Metadata(String title, String identifier, String publisher, String format, String date,
                        String source, List<String> types, List<String> subjects, List<String> rights,
                        List<String> relations, List<String> languages, List<String> descriptions,
                        List<String> creators, List<String> coverages, List<String> contributors,
                        List<String> narrators, Long size, List<Meta> metas) {
            this.title = title;
            this.identifier = identifier;
            this.publisher = publisher;
            this.format = format;
            this.date = date;
            this.source = source;
            this.types = types;
            this.subjects = subjects;
            this.rights = rights;
            this.relations = relations;
            this.languages = languages;
            this.descriptions = descriptions;
            this.creators = creators;
            this.coverages = coverages;
            this.contributors = contributors;
            this.narrators = narrators;
            this.size = size;
            this.metas = metas;
        }

        public String getTitle() {
            return title;
        }

        public String getIdentifier() {
            return identifier;
        }

        public String getPublisher() {
            return publisher;
        }

        public String getFormat() {
            return format;
        }

        public String getDate() {
            return date;
        }

        public String getSource() {
            return source;
        }

        public List<String> getTypes() {
            return types;
        }

        public List<String> getSubjects() {
            return subjects;
        }

        public List<String> getRights() {
            return rights;
        }

        public List<String> getRelations() {
            return relations;
        }

        public List<String> getLanguages() {
            return languages;
        }

        public List<String> getDescriptions() {
            return descriptions;
        }

        public List<String> getCreators() {
            return creators;
        }

        public List<String> getCoverages() {
            return coverages;
        }

        public List<String> getContributors() {
            return contributors;
        }

        public List<String> getNarrators() {
            return narrators;
        }

        public Long getSize() {
            return size;
        }

        public List<Meta> getMetas() {
            return metas;
        }

        public static Metadata parse(Element element) {
            String title = XmlUtil.childText(element, DC, "title");
            String identifier = XmlUtil.childText(element, DC, "identifier");
            String publisher = XmlUtil.childText(element, DC, "publisher");
            String format = XmlUtil.childText(element, DC, "format");
            String date = XmlUtil.childText(element, DC, "date");
            String source = XmlUtil.childText(element, DC, "source");
            List<String> types = XmlUtil.childTexts(element, DC, "type");
            List<String> subjects = XmlUtil.childTexts(element, DC, "subject");
            List<String> rights = XmlUtil.childTexts(element, DC, "rights");
            List<String> relations = XmlUtil.childTexts(element, DC, "relation");
            List<String> languages = XmlUtil.childTexts(element, DC, "language");
            List<String> descriptions = XmlUtil.childTexts(element, DC, "description");
            List<String> creators = XmlUtil.childTexts(element, DC, "creator");
            List<String> coverages = XmlUtil.childTexts(element, DC, "coverage");
            List<String> contributors = XmlUtil.childTexts(element, DC, "contributor");
            List<String> narrators = XmlUtil.childTexts(element, DAISY_ONLINE, "narrator");
            Long size = XmlUtil.parseLong(XmlUtil.childText(element, DAISY_ONLINE, "size"));
            List<Meta> metas = Meta.parseAll(element);
            return new Metadata(title, identifier, publisher, format, date, source, types, subjects,
                    rights, relations, languages, descriptions, creators, coverages, contributors,
                    narrators, size, metas);
        }
    }

    /**
     * A single {@code meta} element: a name/content pair of opaque metadata.
     */
    public static final class Meta {

        private final String name;
        private final String content;

        public Meta(String name, String content) {
            this.name = name;
            this.content = content;
        }

        public String getName() {
            return name;
        }

        public String getContent() {
            return content;
        }

        static List<Meta> parseAll(Element parent) {
            List<Element> elements = XmlUtil.childElements(parent, DAISY_ONLINE, "meta");
            if (elements.isEmpty()) {
                return Collections.emptyList();
            }
            List<Meta> result = new java.util.ArrayList<>(elements.size());
            for (Element element : elements) {
                result.add(new Meta(XmlUtil.attribute(element, "name"), XmlUtil.attribute(element, "content")));
            }
            return result;
        }
    }
}
