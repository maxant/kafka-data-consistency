'use strict';

function Root() {
    return (
<table width="100%" height="100%">
    <tr>
        <td colspan="2" align="center">
            <h2><img height="50px" src="k.jpg" style="vertical-align: text-bottom;">AYS Insurance Ltd.</h2>
        </td>
        <td align="right" valign="top">
            <small><a href="#" onclick="clearData();">clear test data</a></small>
        </td>
    </tr>
    <tr><td colspan="3">
        <hr>
    </td></tr>
    <tr>
        <td width="20%" valign="top">
            <div id="menu"><i class="fas fa-ellipsis-v"></i>&nbsp;Menu</div>
        </td>
        <td width="60%" valign="top">
            <div id="partner" class="tile-group">
                <table>
                    <tr><td class='tile'>
                        <div class='tile-title'><i class="fas fa-user"></i>&nbsp;<b>Ant Kutschera</b></div>
                        <div class='tile-body'>
                            C-4837-4536<br>
                            Ch. des chiens 69<br>
                            1000 Marbach<br>
                            +41 77 888 99 00<br>
                        </div>
                        <td></tr>
                </table>
            </div>
            <hr>
            <div id="contracts" class="tile-group">
                <table>
                    <tr><td class='tile'>
                        <div class='tile-title'><i class="fas fa-file-contract"></i>&nbsp;<b>V-9087-4321</b></div>
                        <div class='tile-body'>
                            House contents insurance<br>
                            incl. fire and theft
                        </div>
                        <td></tr>
                </table>
            </div>
            <hr>
            <div id="claims" class="tile-group">claims</div>
            <br>
            <button onclick="createClaim();">Create new claim...</button>
        </td>
        <td width="20%" valign="top">
            <div id="tasks" class="tile-group">tasks</div>
            <hr>
            <div id="documents" class="tile-group">
                <table>
                    <tr><td class='tile'>
                        <div class='tile-title'><i class="far fa-file-alt"></i>&nbsp;<b>D-5843-79684</b></div>
                        <div class='tile-body'>
                            Contract Document<br>
                            2018-08-01
                        </div>
                        <td></tr>
                </table>
            </div>
        </td>
    </tr>
</table>
    );
}
