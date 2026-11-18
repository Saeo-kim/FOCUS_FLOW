package com.example.project_flow.security;

import java.util.List;

/**
 * The safe defaults that govern AI and human access. They are recorded in the system as an approved
 * policy at startup (see SecurityDefaultsSeeder). Changing any of them requires a code change, tests,
 * and the Owner's approval. Approving a policy never changes these rules.
 */
public final class SecurityDefaults {

    public static final String POLICY_KEY = "system-defaults";

    public static final List<String> RULES = List.of(
            "PUBLIC information may be used by AI tools, including external providers.",
            "INTERNAL information goes only to authorized company AI tools. It is never sent to an external provider.",
            "CONFIDENTIAL information is never sent to an external provider. Inside the company it needs a grant, and an AI seat needs an Owner-approved request for each use.",
            "RESTRICTED information is never sent to an external provider and is never stored in content. No AI seat may receive it. A person needs a grant with a business justification AND an Owner-approved request for each use.",
            "Credentials, keys and passwords are never transmitted, in any category.",
            "Personal data is redacted before release to any AI tool.",
            "No automatic override exists. An exception needs a code change, tests, and the Owner's approval.",
            "Approved policies do not change enforcement rules. Enforcement changes only through the rules above.",
            "Nobody can approve their own request, and an AI seat can never approve anything.");

    private SecurityDefaults() {
    }
}
