package app.extremetube.extension;

/**
 * Network-free About page used by Extreme Tube.
 * No remote images, social links, update checks or tracking endpoints are referenced here.
 */
@SuppressWarnings("unused")
public final class ExtremeAboutHtml {
    private ExtremeAboutHtml() {
    }

    public static String build() {
        return "<!doctype html>"
                + "<html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<style>"
                + "html,body{margin:0;padding:0;background:#0f0f0f;color:#fff;font-family:sans-serif;}"
                + "body{display:flex;align-items:center;justify-content:center;min-height:100vh;}"
                + ".wrap{text-align:center;padding:36px 24px;width:100%;box-sizing:border-box;}"
                + ".logo{font-size:92px;line-height:1.1;margin-bottom:18px;}"
                + ".name{font-size:32px;font-weight:700;margin:0 0 8px;}"
                + ".sub{font-size:18px;color:#bdbdbd;margin:0;}"
                + ".card{margin:32px auto 0;max-width:520px;background:#1c1c1c;border-radius:22px;padding:22px;}"
                + ".cardTitle{font-size:20px;font-weight:700;margin-bottom:8px;}"
                + ".cardText{font-size:15px;line-height:1.5;color:#c8c8c8;}"
                + "</style></head><body><div class=\"wrap\">"
                + "<div class=\"logo\">🕶️</div>"
                + "<div class=\"name\">Dark Ironman</div>"
                + "<div class=\"sub\">Extreme Tube</div>"
                + "<div class=\"card\"><div class=\"cardTitle\">Extreme</div>"
                + "<div class=\"cardText\">Local settings page. No Morphe social, donation, website, update-check or About network links are loaded here.</div></div>"
                + "</div></body></html>";
    }
}
