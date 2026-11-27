package com.example.dz.server.users.service

/**
 * The two pictures a profile can have, and the one size each is stored at.
 *
 * The app crops to exactly these shapes before uploading. The server still checks, and stores its
 * own re-encoded copy at [width] × [height], so what is kept never depends on the client.
 *
 * - Avatar: square (shown as a circle). 512 leaves room above the largest place it is drawn today,
 *   96dp, about 290 pixels on a 3x screen.
 * - Banner: 3:1 across the full width. 1500 covers the widest phones (about 1290 pixels across).
 *
 * @param minWidth smaller than this and the picture would be visibly blurry once stretched to size.
 */
enum class ProfileImageKind(
    val folder: String,
    val width: Int,
    val height: Int,
    val minWidth: Int,
) {
    Avatar(folder = "avatars", width = 512, height = 512, minWidth = 256),
    Banner(folder = "banners", width = 1500, height = 500, minWidth = 750);

    val aspectRatio: Double get() = width.toDouble() / height
    val minHeight: Int get() = minWidth * height / width
}
