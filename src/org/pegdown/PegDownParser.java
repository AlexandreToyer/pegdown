package org.pegdown;

import org.parboiled.Action;
import org.parboiled.BaseParser;
import org.parboiled.Context;
import org.parboiled.Rule;
import org.parboiled.common.StringUtils;
import org.parboiled.google.base.Function;
import org.parboiled.support.Cached;

import static org.parboiled.trees.TreeUtils.addChild;

@SuppressWarnings({"InfiniteRecursion"})
public class PegDownParser extends BaseParser<AstNode> implements AstNodeType {

    private static final String[] HTML_TAGS = new String[]{"address", "blockquote", "center", "dir", "div", "dl",
            "fieldset", "form", "h1", "h2", "h3", "h4", "h5", "h6", "hr", "isindex", "menu", "noframes", "noscript",
            "ol", "p", "pre", "table", "ul", "dd", "dt", "frameset", "li", "tbody", "td", "tfoot", "th", "thead", "tr",
            "script", "style"};

    protected final boolean suppressHtml;

    public PegDownParser(boolean suppressHtml) {
        this.suppressHtml = suppressHtml;
    }

    Rule Doc() {
        return sequence(
                CREATE(NONE),
                zeroOrMore(
                        sequence(Block(), UP2(ATTACH(LAST_VALUE())))
                )
        );
    }

    Rule Block() {
        return sequence(
                zeroOrMore(BlankLine()),
                firstOf(BlockQuote(), Verbatim(), Note(), Reference(), HorizontalRule(), Heading(), OrderedList(),
                        BulletList(), HtmlBlock(), Para(), Plain())
        );
    }

    Rule Para() {
        return sequence(
                sequence(NonindentSpace(), Inlines(), SET(), oneOrMore(BlankLine())),
                SET(LAST_VALUE().setType(PARA))
        );
    }

    Rule Plain() {
        return sequence(Inlines(), SET(LAST_VALUE().setType(PLAIN)));
    }

    Rule BlockQuote() {
        return oneOrMore(
                sequence(
                        '>', optional(' '), Line(), UP(CREATE(BLOCKQUOTE, LAST_VALUE())),
                        zeroOrMore(
                                sequence(
                                        testNot('>'),
                                        testNot(BlankLine()),
                                        Line(), UP3(ATTACH(LAST_VALUE()))
                                )
                        ),
                        zeroOrMore(
                                sequence(BlankLine(), UP3(ATTACH(LAST_VALUE())))
                        )
                )
        );
    }

    Rule Verbatim() {
        return oneOrMore(
                sequence(
                        zeroOrMore(BlankLine()),
                        NonblankIndentedLine(), UP(CREATE(VERBATIM, LAST_VALUE())),
                        zeroOrMore(
                                sequence(NonblankIndentedLine(), UP3(ATTACH(LAST_VALUE())))
                        )
                )
        );
    }

    Rule HorizontalRule() {
        return sequence(
                NonindentSpace(), firstOf(
                        sequence('*', Sp(), '*', Sp(), '*', zeroOrMore(sequence(Sp(), '*'))),
                        sequence('-', Sp(), '-', Sp(), '-', zeroOrMore(sequence(Sp(), '-'))),
                        sequence('_', Sp(), '_', Sp(), '_', zeroOrMore(sequence(Sp(), '_')))
                ), Sp(), Newline(), oneOrMore(BlankLine()),
                CREATE(HRULE)
        );
    }

    //************* HEADING ****************

    Rule Heading() {
        return firstOf(AtxHeading(), SetextHeading());
    }

    Rule AtxHeading() {
        return sequence(
                AtxStart(), SET(),
                Sp(),
                oneOrMore(
                        sequence(AtxInline(), UP2(ATTACH(LAST_VALUE())))
                ),
                optional(sequence(Sp(), zeroOrMore('#'), Sp())),
                Newline()
        );
    }

    Rule AtxStart() {
        return sequence(
                firstOf("######", "#####", "####", "###", "##", "#"),
                CREATE(H1 + LAST_TEXT().length() - 1)
        );
    }

    Rule AtxInline() {
        return sequence(testNot(Newline()), testNot(sequence(Sp(), zeroOrMore('#'), Sp(), Newline())), Inline());
    }

    Rule SetextHeading() {
        return firstOf(SetextHeading1(), SetextHeading2());
    }

    Rule SetextHeading1() {
        return sequence(
                SetextInline(), CREATE(H1, LAST_VALUE()),
                oneOrMore(
                        sequence(SetextInline(), UP2(ATTACH(LAST_VALUE())))
                ),
                Newline(), NOrMore('=', 3), Newline()
        );
    }

    Rule SetextHeading2() {
        return sequence(
                SetextInline(), CREATE(H2, LAST_VALUE()),
                oneOrMore(
                        sequence(SetextInline(), UP2(ATTACH(LAST_VALUE())))
                ),
                Newline(), NOrMore('-', 3), Newline()
        );
    }

    Rule SetextInline() {
        return sequence(testNot(Endline()), Inline());
    }

    //************* LISTS ****************

    Rule BulletList() {
        return sequence(
                test(Bullet()),
                firstOf(ListTight(), ListLoose()),
                SET(LAST_VALUE().setType(LIST_BULLET))
        );
    }

    Rule OrderedList() {
        return sequence(
                test(Enumerator()),
                firstOf(ListTight(), ListLoose()),
                SET(LAST_VALUE().setType(LIST_ORDERED))
        );
    }

    Rule ListTight() {
        return sequence(
                ListItem(), CREATE(NONE, LAST_VALUE().setType(LISTITEM_TIGHT)),
                zeroOrMore(
                        sequence(ListItem(), UP2(ATTACH(LAST_VALUE().setType(LISTITEM_TIGHT))))
                ),
                zeroOrMore(BlankLine()),
                testNot(firstOf(Bullet(), Enumerator()))
        );
    }

    Rule ListLoose() {
        return sequence(
                ListItem(), CREATE(NONE, LAST_VALUE().setType(LISTITEM_LOOSE)),
                zeroOrMore(BlankLine()),
                zeroOrMore(
                        sequence(
                                ListItem(),
                                UP2(ATTACH(LAST_VALUE().setType(LISTITEM_LOOSE))),
                                zeroOrMore(BlankLine())
                        )
                )
        );
    }

    Rule ListItem() {
        return sequence(
                firstOf(Bullet(), Enumerator()),
                ListBlock(), CREATE(NONE, LAST_VALUE()),
                zeroOrMore(
                        sequence(
                                zeroOrMore(BlankLine()),
                                oneOrMore(
                                        sequence(
                                                Indent(),
                                                ListBlock(),
                                                UP4(ATTACH(LAST_VALUE()))
                                        )
                                )
                        )
                )
        );
    }

    Rule ListBlock() {
        return sequence(
                Line(), CREATE(LISTITEMBLOCK, LAST_VALUE()),
                zeroOrMore(
                        sequence(ListBlockLine(), UP2(ATTACH(LAST_VALUE())))
                )
        );
    }

    Rule ListBlockLine() {
        return sequence(
                testNot(sequence(optional(Indent()), firstOf(Bullet(), Enumerator()))),
                testNot(BlankLine()),
                testNot(HorizontalRule()),
                OptionallyIndentedLine()
        );
    }

    Rule Enumerator() {
        return sequence(NonindentSpace(), oneOrMore(Digit()), '.', oneOrMore(Spacechar()));
    }

    Rule Bullet() {
        return sequence(testNot(HorizontalRule()), NonindentSpace(), charSet("+*-"), oneOrMore(Spacechar()));
    }

    //************* HTML BLOCK ****************

    Rule HtmlBlock() {
        return sequence(
                sequence(
                        firstOf(HtmlBlockInTags(), HtmlComment(), HtmlBlockSelfClosing()), SET(),
                        oneOrMore(BlankLine())
                ),
                suppressHtml || CREATE(HTMLBLOCK, LAST_TEXT())
        );
    }

    Rule HtmlBlockInTags() {
        return FirstOfAllHtmlTagsFor(new Function<String, Rule>() {
            public Rule apply(String tag) {
                return sequence(
                        HtmlBlockOpen(tag),
                        zeroOrMore(firstOf(HtmlBlockInTags(), sequence(testNot(HtmlBlockClose(tag)), any()))),
                        HtmlBlockClose(tag)
                );
            }
        });
    }

    Rule HtmlBlockSelfClosing() {
        return sequence('<', Spn1(), HtmlBlockType(), Spn1(), zeroOrMore(HtmlAttribute()), '/', Spn1(), '>');
    }

    Rule HtmlBlockType() {
        return FirstOfAllHtmlTagsFor(new Function<String, Rule>() {
            public Rule apply(String tag) {
                return stringIgnoreCase(tag);
            }
        });
    }

    Rule FirstOfAllHtmlTagsFor(Function<String, Rule> function) {
        Rule[] rules = new Rule[HTML_TAGS.length];
        for (int i = 0; i < HTML_TAGS.length; i++) {
            rules[i] = function.apply(HTML_TAGS[i]);
        }
        return firstOf(rules);
    }

    Rule HtmlBlockOpen(String name) {
        return sequence('<', Spn1(), stringIgnoreCase(name), Spn1(), zeroOrMore(HtmlAttribute()), '>');
    }

    Rule HtmlBlockClose(String name) {
        return sequence('<', Spn1(), '/', stringIgnoreCase(name), Spn1(), '>');
    }

    //************* INLINES ****************

    Rule Inlines() {
        return sequence(
                InlineOrIntermediateEndline(), CREATE(NONE, LAST_VALUE()),
                zeroOrMore(
                        sequence(InlineOrIntermediateEndline(), UP2(ATTACH(LAST_VALUE())))
                ),
                optional(Endline())
        );
    }

    Rule InlineOrIntermediateEndline() {
        return firstOf(
                sequence(testNot(Endline()), Inline()),
                sequence(Endline(), test(Inline()))
        );
    }

    Rule Inline() {
        return firstOf(Str(), Endline(), UlOrStarLine(), Space(), Strong(), Emph(), Image(), Link(), NoteReference(),
                InlineNote(), Code(), RawHtml(), Entity(), EscapedChar(), Ellipsis(), EmDash(), EnDash(),
                SingleQuoted(), DoubleQuoted(), Apostrophe(), Symbol());
    }

    Rule Endline() {
        return firstOf(LineBreak(), TerminalEndline(), NormalEndline());
    }

    Rule LineBreak() {
        return sequence("  ", NormalEndline(), CREATE(LINEBREAK));
    }

    Rule TerminalEndline() {
        return sequence(Sp(), Newline(), eoi());
    }

    Rule NormalEndline() {
        return sequence(
                Sp(), Newline(),
                testNot(
                        firstOf(
                                BlankLine(),
                                '>',
                                AtxStart(),
                                sequence(Line(), firstOf(NOrMore('=', 3), NOrMore('-', 3)), Newline())
                        )
                ),
                CREATE(SPACE, "\n")
        );
    }

    //************* EMPHASIS / STRONG ****************

    // This keeps the parser from getting bogged down on long strings of '*' or '_',
    // or strings of '*' or '_' with space on each side:
    Rule UlOrStarLine() {
        return sequence(
                firstOf(CharLine('_'), CharLine('*')),
                CREATE(TEXT, LAST_TEXT())
        );
    }

    Rule CharLine(char c) {
        return firstOf(NOrMore(c, 4), sequence(Spacechar(), oneOrMore(c), test(Spacechar())));
    }

    Rule Emph() {
        return sequence(
                firstOf(EmphOrStrong("*"), EmphOrStrong("_")),
                SET(LAST_VALUE().setType(EMPH))
        );
    }

    Rule Strong() {
        return sequence(
                firstOf(EmphOrStrong("**"), EmphOrStrong("__")),
                SET(LAST_VALUE().setType(STRONG))
        );
    }

    Rule EmphOrStrong(String chars) {
        return sequence(
                EmphOrStrongOpen(chars),
                CREATE(NONE),
                zeroOrMore(
                        sequence(
                                testNot(EmphOrStrongClose(chars)),
                                Inline(), UP2_ATTACH_LAST_VALUE
                        )
                ),
                EmphOrStrongClose(chars), ATTACH_LAST_VALUE
        );
    }

    Rule EmphOrStrongOpen(String chars) {
        return sequence(
                testNot(CharLine(chars.charAt(0))),
                chars,
                testNot(Spacechar()),
                testNot(Newline())
        );
    }

    @Cached
    Rule EmphOrStrongClose(String chars) {
        return sequence(
                testNot(Spacechar()),
                testNot(Newline()),
                Inline(), SET,
                chars.length() == 1 ? testNot(EmphOrStrong(chars + chars)) : empty(),
                chars.charAt(0) == '_' ? testNot(Alphanumeric()) : empty(),
                '*'
        );
    }

    //************* LINKS ****************

    Rule Image() {
        return sequence('!', firstOf(ExplicitLink(), ReferenceLink()));
    }

    Rule Link() {
        return firstOf(ExplicitLink(), ReferenceLink(), AutoLinkUrl(), AutoLinkEmail());
    }

    Rule ExplicitLink() {
        return sequence(Label(), Spn1(), '(', Sp(), Source(), Spn1(), optional(Title()), Sp(), ')');
    }

    Rule ReferenceLink() {
        return firstOf(ReferenceLinkSingle(), ReferenceLinkDouble());
    }

    Rule ReferenceLinkSingle() {
        return sequence(Label(), optional(sequence(Spn1(), "[]")));
    }

    Rule ReferenceLinkDouble() {
        return sequence(Label(), Spn1(), testNot("[]"), Label());
    }

    Rule Source() {
        return firstOf(sequence('<', SourceContents(), '>'), SourceContents());
    }

    Rule SourceContents() {
        return oneOrMore(firstOf(
                oneOrMore(sequence(testNot('('), testNot(')'), testNot('>'), Nonspacechar())),
                sequence('(', SourceContents(), ')')
        ));
    }

    Rule Title() {
        return firstOf(TitleSingle(), TitleDouble());
    }

    Rule TitleSingle() {
        return sequence('\'', zeroOrMore(sequence(
                testNot(sequence('\'', Sp(), firstOf(')', Newline()))), testNot(Newline()), any()
        )), '\'');
    }

    Rule TitleDouble() {
        return sequence('"', zeroOrMore(sequence(
                testNot(sequence('"', Sp(), firstOf(')', Newline()))), testNot(Newline()), any()
        )), '"');
    }

    Rule AutoLinkUrl() {
        return sequence('<', oneOrMore(Letter()), "://",
                oneOrMore(sequence(testNot(Newline()), testNot('>'), any())), '>');
    }

    Rule AutoLinkEmail() {
        return sequence('<', oneOrMore(firstOf(Alphanumeric(), charSet("-+_"))), '@',
                oneOrMore(sequence(testNot(Newline()), testNot('>'), any())), '>');
    }

    //************* REFERENCE ****************

    Rule Reference() {
        return sequence(NonindentSpace(), testNot("[]"), Label(), ':', Spn1(), RefSrc(), Spn1(), optional(RefTitle()),
                zeroOrMore(BlankLine()));
    }

    Rule Label() {
        return sequence('[', zeroOrMore(sequence(testNot(']'), Inline())), ']');
    }

    Rule RefSrc() {
        return oneOrMore(Nonspacechar());
    }

    Rule RefTitle() {
        return firstOf(RefTitleSingle(), RefTitleDouble(), RefTitleParens());
    }

    Rule RefTitleSingle() {
        return sequence('\'', zeroOrMore(
                sequence(testNot(firstOf(sequence('\'', Sp(), Newline()), Newline())), any())
        ), '\'');
    }

    Rule RefTitleDouble() {
        return sequence('"', zeroOrMore(
                sequence(testNot(firstOf(sequence('"', Sp(), Newline()), Newline())), any())
        ), '"');
    }

    Rule RefTitleParens() {
        return sequence('(', zeroOrMore(
                sequence(testNot(firstOf(sequence(')', Sp(), Newline()), Newline())), any())
        ), ')');
    }

    //************* CODE ****************

    Rule Code() {
        return firstOf(
                sequence(Ticks1(), Sp(), oneOrMore(firstOf(
                        sequence(testNot('`'), Nonspacechar()),
                        sequence(testNot(Ticks1()), oneOrMore('`')),
                        sequence(testNot(sequence(Sp(), Ticks1())),
                                firstOf(Spacechar(), sequence(Newline(), testNot(BlankLine()))))
                )), Sp(), Ticks1()),
                sequence(Ticks2(), Sp(), oneOrMore(firstOf(
                        sequence(testNot('`'), Nonspacechar()),
                        sequence(testNot(Ticks2()), oneOrMore('`')),
                        sequence(testNot(sequence(Sp(), Ticks2())),
                                firstOf(Spacechar(), sequence(Newline(), testNot(BlankLine()))))
                )), Sp(), Ticks2()),
                sequence(Ticks3(), Sp(), oneOrMore(firstOf(
                        sequence(testNot('`'), Nonspacechar()),
                        sequence(testNot(Ticks3()), oneOrMore('`')),
                        sequence(testNot(sequence(Sp(), Ticks3())),
                                firstOf(Spacechar(), sequence(Newline(), testNot(BlankLine()))))
                )), Sp(), Ticks3()),
                sequence(Ticks4(), Sp(), oneOrMore(firstOf(
                        sequence(testNot('`'), Nonspacechar()),
                        sequence(testNot(Ticks4()), oneOrMore('`')),
                        sequence(testNot(sequence(Sp(), Ticks4())),
                                firstOf(Spacechar(), sequence(Newline(), testNot(BlankLine()))))
                )), Sp(), Ticks4()),
                sequence(Ticks5(), Sp(), oneOrMore(firstOf(
                        sequence(testNot('`'), Nonspacechar()),
                        sequence(testNot(Ticks5()), oneOrMore('`')),
                        sequence(testNot(sequence(Sp(), Ticks5())),
                                firstOf(Spacechar(), sequence(Newline(), testNot(BlankLine()))))
                )), Sp(), Ticks5())
        );
    }

    Rule Ticks1() {
        return sequence('`', testNot('`'));
    }

    Rule Ticks2() {
        return sequence("``", testNot('`'));
    }

    Rule Ticks3() {
        return sequence("```", testNot('`'));
    }

    Rule Ticks4() {
        return sequence("````", testNot('`'));
    }

    Rule Ticks5() {
        return sequence("`````", testNot('`'));
    }

    //************* RAW HTML ****************

    Rule RawHtml() {
        return firstOf(HtmlComment(), HtmlTag());
    }

    Rule HtmlComment() {
        return sequence("<!--", zeroOrMore(sequence(testNot("-->"), any())), "-->");
    }

    Rule HtmlTag() {
        return sequence('<', Spn1(), optional('/'), oneOrMore(Alphanumeric()), Spn1(), zeroOrMore(HtmlAttribute()),
                optional('/'), Spn1(), '>');
    }

    Rule HtmlAttribute() {
        return sequence(
                oneOrMore(firstOf(Alphanumeric(), '-')),
                Spn1(),
                optional(sequence('=', Spn1(), firstOf(Quoted(), oneOrMore(sequence(testNot('>'), Nonspacechar()))))),
                Spn1()
        );
    }

    Rule Quoted() {
        return firstOf(
                sequence('"', zeroOrMore(sequence(testNot('"'), any())), '"'),
                sequence('\'', zeroOrMore(sequence(testNot('\''), any())), '\'')
        );
    }

    //************* LINES ****************

    Rule NonblankIndentedLine() {
        return sequence(testNot(BlankLine()), IndentedLine());
    }

    Rule BlankLine() {
        return sequence(Sp(), Newline());
    }

    Rule IndentedLine() {
        return sequence(Indent(), Line());
    }

    Rule OptionallyIndentedLine() {
        return sequence(optional(Indent()), Line());
    }

    Rule Line() {
        return firstOf(
                sequence(zeroOrMore(sequence(testNot('\r'), testNot('\n'), any())), Newline()),
                sequence(oneOrMore(any()), eoi())
        );
    }

    //************* QUOTES ****************

    Rule SingleQuoted() {
        return sequence(SingleQuoteStart(), oneOrMore(sequence(testNot(SingleQuoteEnd()), Inline())), SingleQuoteEnd());
    }

    Rule SingleQuoteStart() {
        return sequence('\'', testNot(charSet(")!],.;:-? \t\n")),
                testNot(sequence(firstOf('s', 't', "m", "ve", "ll", "re"), testNot(Alphanumeric()))));
    }

    Rule SingleQuoteEnd() {
        return sequence('\'', testNot(Alphanumeric()));
    }

    Rule DoubleQuoted() {
        return sequence('"', oneOrMore(sequence(testNot('"'), Inline())), '"');
    }

    //************* NOTES ****************

    Rule InlineNote() {
        return sequence("^[", oneOrMore(sequence(testNot(']'), Inline())), ']');
    }

    Rule NoteReference() {
        return RawNoteReference();
    }

    Rule RawNoteReference() {
        return sequence("[^", oneOrMore(sequence(testNot(Newline()), testNot(']'), any())), ']');
    }

    Rule Note() {
        return sequence(NonindentSpace(), RawNoteReference(), ':', Sp(), RawNoteBlock(),
                zeroOrMore(sequence(test(Indent()), RawNoteBlock())));
    }

    Rule RawNoteBlock() {
        return sequence(oneOrMore(sequence(testNot(BlankLine()), OptionallyIndentedLine())), zeroOrMore(BlankLine()));
    }

    //************* ENTITIES ****************

    Rule Entity() {
        return firstOf(HexEntity(), DecEntity(), CharEntity());
    }

    Rule HexEntity() {
        return sequence("&", charSet("xX"), oneOrMore(firstOf(Digit(), charRange('a', 'f'), charRange('A', 'F'))), ';');
    }

    Rule DecEntity() {
        return sequence("&#", oneOrMore(Digit()), ';');
    }

    Rule CharEntity() {
        return sequence('&', oneOrMore(Alphanumeric()), ';');
    }

    //************* BASICS ****************

    Rule Str() {
        return sequence(
                sequence(NormalChar(), zeroOrMore(sequence(zeroOrMore('_'), NormalChar()))),
                CREATE(TEXT, LAST_TEXT())
        );
    }

    Rule Space() {
        return sequence(oneOrMore(Spacechar()), CREATE(SPACE, " "));
    }

    Rule Spn1() {
        return sequence(Sp(), optional(sequence(Newline(), Sp())));
    }

    Rule Sp() {
        return zeroOrMore(Spacechar());
    }

    Rule Spacechar() {
        return charSet(" \t");
    }

    Rule Nonspacechar() {
        return sequence(testNot(Spacechar()), testNot(Newline()), any());
    }

    Rule NormalChar() {
        return sequence(testNot(firstOf(SpecialChar(), Spacechar(), Newline())), any());
    }

    Rule EscapedChar() {
        return sequence('\\', testNot(Newline()), any(), CREATE(TEXT, LAST_TEXT()));
    }

    Rule Symbol() {
        return sequence(SpecialChar(), CREATE(TEXT, LAST_TEXT()));
    }

    Rule SpecialChar() {
        return charSet("*_`&[]<!\\.-'\"^");
    }

    Rule Newline() {
        return firstOf('\n', sequence('\r', optional('\n')));
    }

    Rule NonindentSpace() {
        return firstOf("   ", "  ", " ", empty());
    }

    Rule Indent() {
        return firstOf('\t', "    ");
    }

    Rule Apostrophe() {
        return sequence('\'', CREATE(APOSTROPHE));
    }

    Rule Ellipsis() {
        return sequence(firstOf("...", ". . ."), CREATE(ELLIPSIS));
    }

    Rule EnDash() {
        return sequence('-', test(Digit()), CREATE(ENDASH));
    }

    Rule EmDash() {
        return sequence(firstOf("---", "--"), CREATE(EMDASH));
    }

    Rule Alphanumeric() {
        return firstOf(Letter(), Digit());
    }

    Rule Letter() {
        return firstOf(charRange('A', 'Z'), charRange('a', 'z'));
    }

    Rule Digit() {
        return charRange('0', '9');
    }

    //************* ACTIONS ****************
    // these Actions are used in Rule methods with parameters, therefore they need to be factored out

    Rule SET = toRule(new Action<AstNode>() {
        public boolean run(Context<AstNode> context) {
            return SET();
        }
    });

    Rule UP2_ATTACH_LAST_VALUE = toRule(new Action<AstNode>() {
        public boolean run(Context<AstNode> context) {
            setContext(context.getParent().getParent());
            return ATTACH(LAST_VALUE());
        }
    });

    Rule ATTACH_LAST_VALUE = toRule(new Action<AstNode>() {
        public boolean run(Context<AstNode> context) {
            return ATTACH(LAST_VALUE());
        }
    });

    //************* HELPERS ****************

    Rule NOrMore(char c, int n) {
        return sequence(StringUtils.repeat(c, n), zeroOrMore(c));
    }

    boolean ATTACH(AstNode astNode) {
        if (astNode != null) addChild(getContext().getNodeValue(), astNode);
        return true;
    }

    // this action is context independent, therefore we can model it directly as an Action
    // so it can also be used in helper methods with parameters
    @Cached
    Rule CREATE(final int type) {
        return toRule(new Action() {
            public boolean run(Context context) {
                return CREATE(type, null, null);
            }
        });
    }

    boolean CREATE(int type, String text) {
        return CREATE(type, text, null);
    }

    boolean CREATE(int type, AstNode child) {
        return CREATE(type, null, child);
    }

    boolean CREATE(int type, String text, AstNode child) {
        AstNode astNode = new AstNode().setType(type).setText(text);
        if (child != null) addChild(astNode, child);
        return SET(astNode);
    }

}
