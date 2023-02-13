package com.softwire.todos.jira;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.FieldSetter;
import org.kohsuke.args4j.spi.Messages;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

import java.util.ArrayList;
import java.util.List;

public class JiraProjectOptionHandler extends org.kohsuke.args4j.spi.OptionHandler<List<JiraProject>> {

    public JiraProjectOptionHandler(
            CmdLineParser parser,
            OptionDef option,
            Setter<List<JiraProject>> setter) {
        super(parser, option, setter);
        if (setter.asFieldSetter()==null)
            throw new IllegalArgumentException("JiraProjectOptionHandler can only work with fields");
    }

    @Override
    public int parseArguments(Parameters params) throws CmdLineException {
        FieldSetter fieldSetter = setter.asFieldSetter();
        List<JiraProject> parsedParams = (List<JiraProject>) fieldSetter.getValue();

        if (parsedParams == null) {
            parsedParams = new ArrayList<>();
            fieldSetter.addValue(parsedParams);
        }

        String rawParameter = params.getParameter(0);

        if (rawParameter.length() == 0) {
            throw new CmdLineException(owner, Messages.MAP_HAS_NO_KEY);
        }

        if (rawParameter.contains("=")) {
            String[] splitParam = rawParameter.split("=", 2);
            parsedParams.add(new JiraProject(splitParam[0], splitParam[1]));
        } else {
            parsedParams.add(new JiraProject(rawParameter, rawParameter));
        }

        return 1;
    }

    @Override
    public String getDefaultMetaVariable() {
        return null;
    }
}
