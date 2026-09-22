package android.net

import android.os.Parcel

/**
 * A minimal [Uri] for JVM unit tests.
 *
 * The mockable android.jar makes `Uri.parse(...)` return null under
 * `unitTests.isReturnDefaultValues = true`, so tests cannot build real uris.
 * [Uri] is abstract with a package-private constructor, so this test-only
 * subclass lives in `android.net` to gain access and keeps a stable [toString]
 * carrying the content-uri string the app logic keys on.
 *
 * Never instantiated in production code (test sources only).
 */
class TestUri(private val value: String) : Uri() {

    override fun toString(): String = value

    override fun equals(other: Any?): Boolean = other is Uri && other.toString() == value

    override fun hashCode(): Int = value.hashCode()

    override fun getScheme(): String? = null

    override fun getAuthority(): String? = null

    override fun getUserInfo(): String? = null

    override fun getPath(): String? = null

    override fun getQuery(): String? = null

    override fun getFragment(): String? = null

    override fun getHost(): String? = null

    override fun getPort(): Int = -1

    override fun getLastPathSegment(): String? = null

    override fun getPathSegments(): MutableList<String> = mutableListOf()

    override fun isHierarchical(): Boolean = true

    override fun isRelative(): Boolean = false

    override fun isAbsolute(): Boolean = true

    override fun normalizeScheme(): Uri = this

    override fun getQueryParameter(key: String?): String? = null

    override fun getQueryParameters(key: String?): List<String?> = emptyList()

    override fun getEncodedQuery(): String? = null

    override fun getEncodedPath(): String? = null

    override fun getEncodedAuthority(): String? = null

    override fun getEncodedFragment(): String? = null

    override fun getEncodedUserInfo(): String? = null

    override fun getSchemeSpecificPart(): String? = null

    override fun getEncodedSchemeSpecificPart(): String? = null

    override fun buildUpon(): Builder? = null

    override fun compareTo(other: Uri?): Int = value.compareTo(other?.toString().orEmpty())

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) = Unit
}