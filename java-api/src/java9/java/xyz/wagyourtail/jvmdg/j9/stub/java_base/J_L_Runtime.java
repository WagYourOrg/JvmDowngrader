package xyz.wagyourtail.jvmdg.j9.stub.java_base;

import xyz.wagyourtail.jvmdg.version.Adapter;
import xyz.wagyourtail.jvmdg.version.Ref;
import xyz.wagyourtail.jvmdg.version.Stub;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class J_L_Runtime {


    @Stub(ref = @Ref("java/lang/Runtime"))
    public static Version version() {
        return Version.INSTANCE;
    }

    @Adapter("java/lang/Runtime$Version")
    public static final class Version implements Comparable<Version> {
        private static final String RUNTIME_VERSION = System.getProperty("java.runtime.version");
        public static final Version INSTANCE;
        private final List<Integer> version;
        private final Optional<String> pre;
        private final Optional<Integer> build;
        private final Optional<String> optional;

        private Version(List<Integer> unmodifiableListOfVersions,
                        Optional<String> pre,
                        Optional<Integer> build,
                        Optional<String> optional) {
            this.version = unmodifiableListOfVersions;
            this.pre = pre;
            this.build = build;
            this.optional = optional;
        }

        static {
            Version version;
            try {
                version = Version.parse(RUNTIME_VERSION);
            } catch (RuntimeException e1) {
                // We need to support java8 versions properly there
                int tmp = RUNTIME_VERSION.indexOf('-');
                String verTrimmed = RUNTIME_VERSION;
                // Put optional in a separate field
                String optional = null;
                if (tmp != -1) {
                    verTrimmed = RUNTIME_VERSION.substring(0, tmp);
                    optional = RUNTIME_VERSION.substring(tmp + 1);
                }
                // Get versions
                String[] tokens = verTrimmed.split("[._]");
                int start = verTrimmed.startsWith("1.") ? 1 : 0;
                Integer[] versions = new Integer[tokens.length - start];
                for (int i = start; i < tokens.length; i++) {
                    versions[i - start] = Integer.parseInt(tokens[i]);
                }
                version = new Version(Arrays.asList(versions), Optional.empty(),
                        Optional.of(versions[0]), Optional.ofNullable(optional));
            }
            INSTANCE = Objects.requireNonNull(version, "version");
        }

        public static Version parse(String s) {
            if (s == null)
                throw new NullPointerException();

            // Shortcut to avoid initializing VersionPattern when creating
            // feature-version constants during startup
            if (isSimpleNumber(s)) {
                return new Version(Collections.singletonList(Integer.parseInt(s)),
                        Optional.empty(), Optional.empty(), Optional.empty());
            }
            Matcher m = VersionPattern.VSTR_PATTERN.matcher(s);
            if (!m.matches()) {
                // If parsing the current runtime version, and we fail to parse it,
                // return a copy of "INSTANCE" instead of throwing an exception
                if (RUNTIME_VERSION.equals(s)) {
                    return new Version(INSTANCE.version, INSTANCE.pre, INSTANCE.build, INSTANCE.optional);
                }
                throw new IllegalArgumentException("Invalid version string: '" + s + "'");
            }

            // $VNUM is a dot-separated list of integers of arbitrary length
            String[] split = m.group(VersionPattern.VNUM_GROUP).split("[._]");
            Integer[] version = new Integer[split.length];
            for (int i = 0; i < split.length; i++) {
                version[i] = Integer.parseInt(split[i]);
            }

            Optional<String> pre = Optional.ofNullable(
                    m.group(VersionPattern.PRE_GROUP));

            String b = m.group(VersionPattern.BUILD_GROUP);
            // $BUILD is an integer
            Optional<Integer> build = (b == null)
                    ? Optional.empty()
                    : Optional.of(Integer.parseInt(b));

            if (pre.isPresent() && !build.isPresent() && pre.get().matches("b\\d+")) {
                build = Optional.of(Integer.parseInt(pre.get().substring(1)));
                pre = Optional.empty();
            }

            Optional<String> optional = Optional.ofNullable(
                    m.group(VersionPattern.OPT_GROUP));

            // empty '+'
            if (!build.isPresent()) {
                if (m.group(VersionPattern.PLUS_GROUP) != null) {
                    if (optional.isPresent()) {
                        if (pre.isPresent())
                            throw new IllegalArgumentException("'+' found with"
                                    + " pre-release and optional components:'" + s
                                    + "'");
                    } else {
                        throw new IllegalArgumentException("'+' found with neither"
                                + " build or optional components: '" + s + "'");
                    }
                } else {
                    if (optional.isPresent() && !pre.isPresent()) {
                        throw new IllegalArgumentException("optional component"
                                + " must be preceded by a pre-release component"
                                + " or '+': '" + s + "'");
                    }
                }
            }
            return new Version(Arrays.asList(version), pre, build, optional);
        }

        private static boolean isSimpleNumber(String s) {
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                char lowerBound = (i > 0) ? '0' : '1';
                if (c < lowerBound || c > '9') {
                    return false;
                }
            }
            return true;
        }

        public int major() {
            return version.get(0);
        }

        public int minor() {
            return version.size() > 1 ? version.get(1) : 0;
        }

        public int security() {
            return version.size() > 2 ? version.get(2) : 0;
        }

        public List<Integer> version() {
            return version;
        }

        public Optional<String> pre() {
            return pre;
        }

        public Optional<Integer> build() {
            return build;
        }

        public Optional<String> optional() {
            return optional;
        }

        @Override
        public int compareTo(Version o) {
            return compare(o, false);
        }

        public int compareToIgnoreOptional(Version obj) {
            return compare(obj, true);
        }

        private int compare(Version obj, boolean ignoreOpt) {
            if (obj == null)
                throw new NullPointerException();

            int ret = compareVersion(obj);
            if (ret != 0)
                return ret;

            ret = comparePre(obj);
            if (ret != 0)
                return ret;

            ret = compareBuild(obj);
            if (ret != 0)
                return ret;

            if (!ignoreOpt)
                return compareOptional(obj);

            return 0;
        }

        private int compareVersion(Version obj) {
            int size = version.size();
            int oSize = obj.version().size();
            int min = Math.min(size, oSize);
            for (int i = 0; i < min; i++) {
                int val = version.get(i);
                int oVal = obj.version().get(i);
                if (val != oVal)
                    return val - oVal;
            }
            return size - oSize;
        }

        private int comparePre(Version obj) {
            Optional<String> oPre = obj.pre();
            if (!pre.isPresent()) {
                if (oPre.isPresent())
                    return 1;
            } else {
                if (!oPre.isPresent())
                    return -1;
                String val = pre.get();
                String oVal = oPre.get();
                if (val.matches("\\d+")) {
                    return (oVal.matches("\\d+")
                            ? (new BigInteger(val)).compareTo(new BigInteger(oVal))
                            : -1);
                } else {
                    return (oVal.matches("\\d+")
                            ? 1
                            : val.compareTo(oVal));
                }
            }
            return 0;
        }

        private int compareBuild(Version obj) {
            Optional<Integer> oBuild = obj.build();
            if (oBuild.isPresent()) {
                return (build.map(integer -> integer.compareTo(oBuild.get())).orElse(-1));
            } else if (build.isPresent()) {
                return 1;
            }
            return 0;
        }

        private int compareOptional(Version obj) {
            Optional<String> oOpt = obj.optional();
            if (!optional.isPresent()) {
                if (oOpt.isPresent())
                    return -1;
            } else {
                return oOpt.map(s -> optional.get().compareTo(s)).orElse(1);
            }
            return 0;
        }

        @Override
        public String toString() {
            StringBuilder sb
                    = new StringBuilder(version.stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(".")));

            pre.ifPresent(v -> sb.append("-").append(v));

            if (build.isPresent()) {
                sb.append("+").append(build.get());
                optional.ifPresent(s -> sb.append("-").append(s));
            } else {
                if (optional.isPresent()) {
                    sb.append(pre.isPresent() ? "-" : "+-");
                    sb.append(optional.get());
                }
            }

            return sb.toString();
        }

        @Override
        public boolean equals(Object obj) {
            boolean ret = equalsIgnoreOptional(obj);
            if (!ret)
                return false;

            Version that = (Version) obj;
            return (this.optional().equals(that.optional()));
        }

        public boolean equalsIgnoreOptional(Object obj) {
            if (this == obj)
                return true;
            if (obj instanceof Version) {
                Version that = (Version) obj;
                return (this.version().equals(that.version())
                        && this.pre().equals(that.pre())
                        && this.build().equals(that.build()));
            }
            return false;
        }

        @Override
        public int hashCode() {
            int h = 1;
            int p = 17;

            h = p * h + version.hashCode();
            h = p * h + pre.hashCode();
            h = p * h + build.hashCode();
            h = p * h + optional.hashCode();

            return h;
        }

        private static class VersionPattern {
            // $VNUM(-$PRE)?(\+($BUILD)?(\-$OPT)?)?
            // RE limits the format of version strings
            // ([1-9][0-9]*(?:(?:\.0)*\.[1-9][0-9]*)*)(?:-([a-zA-Z0-9]+))?(?:(\+)(0|[1-9][0-9]*)?)?(?:-([-a-zA-Z0-9.]+))?

            static final String VNUM_GROUP = "VNUM";
            static final String PRE_GROUP = "PRE";
            static final String PLUS_GROUP = "PLUS";
            static final String BUILD_GROUP = "BUILD";
            static final String OPT_GROUP = "OPT";
            private static final String VNUM
                    = "(?:1\\.)?(?<VNUM>[1-9][0-9]*(?:(?:\\.0)*[._][1-9][0-9]*)*)";
            private static final String PRE = "(?:-(?<PRE>[a-zA-Z0-9]+))?";
            private static final String BUILD
                    = "(?:(?<PLUS>\\+|-b)(?<BUILD>[0-9]+)?)?";
            private static final String OPT = "(?:-(?<OPT>[-a-zA-Z0-9.]+))?";
            private static final String VSTR_FORMAT = VNUM + PRE + BUILD + OPT;
            static final Pattern VSTR_PATTERN = Pattern.compile(VSTR_FORMAT);
        }
    }
}
