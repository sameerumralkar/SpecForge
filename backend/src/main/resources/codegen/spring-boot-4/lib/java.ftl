<#-- Helpers shared by the Java templates. -->
<#-- Text for a Javadoc or line comment, with comment terminators defused. -->
<#function doc text>
  <#return (text!"")?replace("*/", "* /")?replace("\n", " ")>
</#function>
<#-- Indents every line after the first of a multi-line code fragment. -->
<#function indent code spaces>
  <#local pad = ""?left_pad(spaces)>
  <#return code?replace("\n", "\n" + pad)>
</#function>
