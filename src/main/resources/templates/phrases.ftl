<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Emoji phrases</title>
</head>
<body>
<h1>Emoji phrases</h1>
<#if phrases?size == 0>
    <p>No phrases yet. POST one to <code>/api/v1/phrases</code>.</p>
<#else>
    <ul>
        <#list phrases as phrase>
            <li>
                <span>${phrase.emoji}</span>
                ${phrase.phrase?html}
                <small>#${phrase.id}</small>
            </li>
        </#list>
    </ul>
</#if>
</body>
</html>
