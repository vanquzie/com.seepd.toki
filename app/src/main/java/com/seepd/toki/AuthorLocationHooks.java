package com.seepd.toki;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;

import io.github.libxposed.api.XposedModule;

/** Adds the author's region at TikTok's shared author-title builder. */
final class AuthorLocationHooks extends HookFeature {
    private static final int REGIONAL_INDICATOR_A = 0x1F1E6;
    private static final String GLOBE = "\uD83C\uDF10";

    AuthorLocationHooks(XposedModule module) {
        super(module);
    }

    int install(ClassLoader classLoader) {
        try {
            Class<?> userType = Class.forName(
                    "com.ss.android.ugc.aweme.profile.model.User",
                    false,
                    classLoader);
            Class<?> awemeType = Class.forName(
                    "com.ss.android.ugc.aweme.feed.model.Aweme",
                    false,
                    classLoader);
            Method buildTitle = findTitleBuilder(classLoader, userType, awemeType);
            if (buildTitle == null) {
                logInfo("author-title builder has no target, skipping");
                return 0;
            }

            Method getAuthor = awemeType.getMethod("getAuthor");
            Method getRegion = userType.getMethod("getRegion");
            buildTitle.setAccessible(true);
            hook(buildTitle)
                    .setId("toki-author-location-title-builder-4643")
                    .intercept(chain -> {
                        Object title = chain.proceed();
                        return addAuthorRegion(
                                title,
                                chain.getArg(1),
                                chain.getArg(2),
                                getAuthor,
                                getRegion);
                    });
            return 1;
        } catch (ClassNotFoundException ignored) {
            return 0;
        } catch (Throwable error) {
            logError("Unable to install the 46.4.3 author-title builder hook", error);
            return 0;
        }
    }

    /**
     * 47.1.3 moved the builder from X.0cvj to X.08zw with an identical
     * signature; try the known exact targets in order, nothing else.
     */
    private static Method findTitleBuilder(
            ClassLoader classLoader, Class<?> userType, Class<?> awemeType) {
        for (String builderClass : new String[]{"X.0cvj", "X.08zw"}) {
            try {
                Class<?> titleBuilderType = Class.forName(
                        builderClass, false, classLoader);
                Method buildTitle = titleBuilderType.getDeclaredMethod(
                        "LIZIZ", String.class, userType, awemeType);
                if (Modifier.isStatic(buildTitle.getModifiers())
                        && buildTitle.getReturnType() == String.class) {
                    return buildTitle;
                }
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                // Try the next known builder.
            }
        }
        return null;
    }

    private static Object addAuthorRegion(
            Object value,
            Object selectedUser,
            Object aweme,
            Method getAuthor,
            Method getRegion
    ) {
        if (!(value instanceof String)) {
            return value;
        }
        String title = (String) value;
        if (title.isEmpty()) {
            return title;
        }
        try {
            Object author = selectedUser != null
                    ? selectedUser
                    : aweme == null ? null : getAuthor.invoke(aweme);
            Object regionValue = author == null ? null : getRegion.invoke(author);
            if (!(regionValue instanceof String)) {
                return title;
            }
            String region = ((String) regionValue).trim();
            if (region.isEmpty()) {
                return title;
            }
            if (region.length() == 2) {
                region = region.toUpperCase(Locale.ROOT);
            }
            return "[" + flagFor(region) + region + "] " + title;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return title;
        }
    }

    private static String flagFor(String region) {
        if (region.length() != 2) {
            return GLOBE;
        }
        char first = region.charAt(0);
        char second = region.charAt(1);
        if (first < 'A' || first > 'Z' || second < 'A' || second > 'Z') {
            return GLOBE;
        }
        return new String(Character.toChars(REGIONAL_INDICATOR_A + first - 'A'))
                + new String(Character.toChars(REGIONAL_INDICATOR_A + second - 'A'));
    }
}
