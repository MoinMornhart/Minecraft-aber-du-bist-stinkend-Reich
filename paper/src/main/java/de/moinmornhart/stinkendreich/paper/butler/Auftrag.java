package de.moinmornhart.stinkendreich.paper.butler;

/** Ein Auftrag, den James Tick für Tick abarbeitet (Erz oder Holz holen und liefern, Truhen sortieren). */
interface Auftrag {
    enum Stand { LAEUFT, FERTIG }

    Stand tick();

    /** Was er gerade tut, für „Was machst du gerade?“ (klein geschrieben, z. B. „sortiert die Truhen“). */
    String status();

    /** Neuer Befehl: aufhören, Risse und Werkzeug weg. */
    void abbrechen();
}
