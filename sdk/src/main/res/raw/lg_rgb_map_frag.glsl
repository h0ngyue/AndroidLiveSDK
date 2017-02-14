varying highp vec2 textureCoordinate;
varying highp vec2 textureCoordinate2;
uniform sampler2D inputImageTexture;
uniform sampler2D inputImageTexture2;
//uniform int mLevel;
void main()
{
    highp float level = 50.0;
    highp vec4 base = texture2D(inputImageTexture, textureCoordinate);
    highp float curR = base.r;
    highp float curG = base.g;
    highp float curB = base.b;
    highp vec2 textureCoordinateMyR = vec2(curR,(level-10.0)/302.0);
    highp vec2 textureCoordinateMyG = vec2(curG,(level+101.0)/302.0);
    highp vec2 textureCoordinateMyB = vec2(curB,(level+202.0)/302.0);
    highp vec4 lookuptabler = texture2D(inputImageTexture2,textureCoordinateMyR);
    highp vec4 lookuptableg = texture2D(inputImageTexture2,textureCoordinateMyG);
    highp vec4 lookuptableb = texture2D(inputImageTexture2,textureCoordinateMyB);
    curR = lookuptabler.r;
    curG = lookuptableg.g;
    curB = lookuptableb.b;
    curR = min(max(curR,0.0),1.0);
    curG = min(max(curG,0.0),1.0);
    curB = min(max(curB,0.0),1.0);
    gl_FragColor = vec4(curR, curG, curB, 1.0);
}