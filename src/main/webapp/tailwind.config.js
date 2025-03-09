module.exports = {
    corePlugins: {
        preflight: false, // Disables Tailwind’s global resets
    },
    content: [
        "./src/**/*.xhtml",
        "./src/**/*.js",
        "./src/**/*.html"
    ],
    theme: {
        extend: {},
    },
    plugins: [],
};