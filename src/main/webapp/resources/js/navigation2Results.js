/**
 * Example function to stop polling (if using PrimeFaces p:poll)
 * and then navigate. You might call this instead if you need to
 * ensure polling stops first.
 *
 * @param {string} widgetVar - The widgetVar of the p:poll component.
 * @param {string} targetUrl - The URL to navigate to (e.g., 'topics/results.xhtml').
 */
function stopPollingAndNavigate(widgetVar, targetUrl) {
    try {
        // Check if PrimeFaces and the specific poll widget exist
        if (typeof PF !== 'undefined' && PF(widgetVar)) {
            PF(widgetVar).stop();
            console.log(`Polling stopped for widget: ${widgetVar}`);
        } else {
            console.warn(`Could not find or stop poll widget: ${widgetVar}`);
        }
    } catch (e) {
        console.error(`Error stopping poll widget: ${widgetVar}`, e);
    }

    // Navigate after attempting to stop the poll
    console.log(`Navigating to: ${targetUrl}`);
    window.location.href = targetUrl;
}