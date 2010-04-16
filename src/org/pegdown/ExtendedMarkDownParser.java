package org.pegdown;

import org.parboiled.Rule;

@SuppressWarnings({"InfiniteRecursion"})
public class ExtendedMarkDownParser extends MarkDownParser {

    @Override
    Rule Inline() {
        return FirstOf(super.Inline(), Apostrophe(), Ellipsis(), EnDash(), EmDash(), SingleQuoted(), DoubleQuoted());
    }

    @Override
    Rule SpecialChar() {
        return FirstOf(super.SpecialChar(), CharSet(".-'\"^"));
    }

    //************* SMARTS ****************

    Rule Apostrophe() {
        return Sequence('\'', create(APOSTROPHE));
    }

    Rule Ellipsis() {
        return Sequence(FirstOf("...", ". . ."), create(ELLIPSIS));
    }

    Rule EnDash() {
        return Sequence('-', Test(Digit()), create(ENDASH));
    }

    Rule EmDash() {
        return Sequence(FirstOf("---", "--"), create(EMDASH));
    }

    //************* QUOTES ****************

    Rule SingleQuoted() {
        return Sequence(SingleQuoteStart(), OneOrMore(Sequence(TestNot(SingleQuoteEnd()), Inline())), SingleQuoteEnd());
    }

    Rule SingleQuoteStart() {
        return Sequence('\'', TestNot(CharSet(")!],.;:-? \t\n")),
                TestNot(Sequence(FirstOf('s', 't', "m", "ve", "ll", "re"), TestNot(Alphanumeric()))));
    }

    Rule SingleQuoteEnd() {
        return Sequence('\'', TestNot(Alphanumeric()));
    }

    Rule DoubleQuoted() {
        return Sequence('"', OneOrMore(Sequence(TestNot('"'), Inline())), '"');
    }

}